import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { api, retryAfterMinutes, type Problem } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, formatMoney, PinInput, TextField, type Currency } from '../ui'
import { amountSchema, cardLabel, pinSchema, useOwnCards } from './money'
import { CardsGate, MoneyDone } from './MoneyViews'

/** Accepts "+994 50 123 45 67", "050 123 45 67" or "501234567"; the API wants +994 and 9 digits. */
function toPhone(s: string): string | null {
  const d = s.replace(/\D/g, '')
  const local = d.length === 12 && d.startsWith('994') ? d.slice(3) : d.length === 10 && d.startsWith('0') ? d.slice(1) : d
  return /^\d{9}$/.test(local) ? `+994${local}` : null
}

// Mirrors PaymentRequest: +994 and 9 digits, own card id, amount.
const schema = z.object({
  fromCardId: z.string().min(1),
  phoneNumber: z.string().transform((s, ctx) => toPhone(s) ?? (ctx.addIssue({ code: 'custom', message: t('pay.phoneInvalid') }), z.NEVER)),
  amount: amountSchema,
})
type Input = z.input<typeof schema>
type Details = z.output<typeof schema> & { currency: Currency; last4: string }
type Flow = { id: string; key: string; details: Details }

const code = (problem: unknown) => (problem as Problem | undefined)?.code

export default function Pay() {
  const { cards, reload, retry } = useOwnCards()
  const [error, setError] = useState<string | null>(null)
  const [flow, setFlow] = useState<Flow | null>(null)
  const [pin, setPin] = useState('')
  const [pinError, setPinError] = useState<string | undefined>()
  const [confirming, setConfirming] = useState(false)
  const [done, setDone] = useState<{ amount: string; currency: Currency; phone: string } | null>(null)
  // Initiate: one key per (phone, card, amount), the server's own fingerprint, so a resend cannot open a second flow.
  // Confirm: one key per flow (the server fingerprints the flow id only), so a resend cannot pay twice.
  const attempt = useRef<{ body: string; key: string } | null>(null)
  const { register, control, handleSubmit, getValues, reset, setError: setFieldError, setValue, formState: { errors, isSubmitting } } =
    useForm<Input, unknown, z.output<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { fromCardId: '', phoneNumber: '', amount: '' } })

  useEffect(() => {
    if (Array.isArray(cards) && cards[0] && !getValues('fromCardId')) setValue('fromCardId', String(cards[0].id))
  }, [cards, getValues, setValue])

  const fromCardId = useWatch({ control, name: 'fromCardId' })
  const from = Array.isArray(cards) ? cards.find((c) => String(c.id) === fromCardId) : undefined

  const initiate = async (form: z.output<typeof schema>) => {
    setError(null)
    const card = Array.isArray(cards) ? cards.find((c) => String(c.id) === form.fromCardId) : undefined
    if (!card) return
    const body = { phoneNumber: form.phoneNumber, fromCardId: Number(form.fromCardId), amount: form.amount }
    const same = `${body.phoneNumber}|${body.fromCardId}|${body.amount}`
    const key = attempt.current?.body === same ? attempt.current.key : crypto.randomUUID()
    attempt.current = { body: same, key }
    try {
      const { data, error: problem, response } = await api.POST('/api/payment/initiate',
        { body, params: { header: { 'Idempotency-Key': key } } })
      if (response.ok && typeof data?.data === 'string' && data.data) {
        attempt.current = null
        setPin(''); setPinError(undefined)
        setFlow({ id: data.data, key: crypto.randomUUID(),
          details: { ...form, currency: card.currency as Currency, last4: card.maskedNumber?.slice(-4) ?? '' } })
        return
      }
      switch (code(problem)) {
        case 'INSUFFICIENT_BALANCE': setFieldError('amount', { message: t('pay.insufficient') }, { shouldFocus: true }); return
        case 'NOT_FOUND': setError(t('pay.cardGone')); reload(); return
        case 'LEGAL_ACCEPTANCE_REQUIRED': setError(t('money.legal')); return
        case 'RESOURCE_BUSY': setError(t('money.busy')); return
        default: setError(t('pay.rejected'))
      }
    } catch {
      setError(t('pay.failed'))
    }
  }

  const confirm = async () => {
    if (!flow || confirming) return
    if (!pinSchema.safeParse(pin).success) { setPinError(t('money.pinInvalid')); return }
    setError(null); setPinError(undefined); setConfirming(true)
    try {
      const { data, error: problem, response } = await api.POST('/api/payment/confirm',
        { body: { flowId: flow.id, pin }, params: { header: { 'Idempotency-Key': flow.key } } })
      const r = data?.data
      if (response.ok && r?.amount && r.currency) {
        setDone({ amount: r.amount, currency: r.currency, phone: flow.details.phoneNumber })
        return
      }
      switch (code(problem)) {
        case 'INVALID_PIN': setPin(''); setPinError(t('money.pinWrong')); return
        case 'PIN_LOCKED': setError(t('money.pinLocked', { minutes: retryAfterMinutes(response) ?? 15 })); return
        case 'PIN_NOT_SET': setError(t('money.pinNotSet')); return
        case 'LEGAL_ACCEPTANCE_REQUIRED': setError(t('money.legal')); return
        case 'RESOURCE_BUSY': setError(t('money.busy')); return
        case 'INSUFFICIENT_BALANCE': setError(t('pay.insufficient')); return
        case 'PAYMENT_ALREADY_PROCESSED': setError(t('pay.already')); return
        case 'NOT_FOUND': setFlow(null); setError(t('pay.flowGone')); return
        default: setError(t('pay.rejected'))
      }
    } catch {
      setError(t('pay.failed')) // same flow, same key: pressing Pay again is a safe retry
    } finally {
      setConfirming(false)
    }
  }

  const again = () => {
    setDone(null); setFlow(null)
    reset({ fromCardId: getValues('fromCardId'), phoneNumber: '', amount: '' })
    reload() // balances changed
  }

  if (done) {
    return <MoneyDone title={t('pay.paid')} amount={done.amount} currency={done.currency}
      detail={t('pay.paidTo', { phone: done.phone })} again={again} againLabel={t('pay.another')} />
  }

  if (flow) {
    const d = flow.details
    return (
      <main className="page">
        <h1>{t('pay.confirmTitle')}</h1>
        <form className="auth-form" onSubmit={(e) => { e.preventDefault(); void confirm() }} noValidate>
          {error && <Alert tone="danger" title={error} />}
          <dl className="summary">
            <dt>{t('money.amount')}</dt><dd>{formatMoney(d.amount, d.currency)}</dd>
            <dt>{t('pay.phone')}</dt><dd>{d.phoneNumber}</dd>
            <dt>{t('money.from')}</dt><dd>•••• {d.last4}</dd>
          </dl>
          <PinInput label={t('money.pin')} value={pin} onChange={setPin} error={pinError} autoFocus />
          <Button type="submit" block loading={confirming}>{t('pay.submit')}</Button>
          {/* the pending flow is left to expire on the server; editing starts a new one */}
          <Button variant="ghost" block disabled={confirming} onClick={() => { setFlow(null); setError(null) }}>{t('pay.edit')}</Button>
        </form>
      </main>
    )
  }

  return (
    <main className="page">
      <h1>{t('pay.title')}</h1>
      <CardsGate cards={cards} retry={retry}>{(list) => (
        <form className="auth-form" onSubmit={(e) => void handleSubmit(initiate)(e)} noValidate>
          {error && <Alert tone="danger" title={error} />}
          <div className="field">
            <label htmlFor="from" className="field-label">{t('money.from')}</label>
            <div className="field-control">
              <select id="from" {...register('fromCardId')}>
                {list.map((c) => <option key={c.id} value={String(c.id)}>{cardLabel(c)}</option>)}
              </select>
            </div>
          </div>
          <TextField label={t('pay.phone')} type="tel" inputMode="tel" autoComplete="off" placeholder="+994 50 123 45 67"
            error={errors.phoneNumber?.message} {...register('phoneNumber')} />
          <TextField label={t('money.amount')} inputMode="decimal" autoComplete="off" placeholder="0.00"
            suffix={from?.currency} error={errors.amount?.message} {...register('amount')} />
          <Button type="submit" block loading={isSubmitting}>{t('pay.continue')}</Button>
        </form>
      )}</CardsGate>
    </main>
  )
}
