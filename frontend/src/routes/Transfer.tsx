import { zodResolver } from '@hookform/resolvers/zod'
import { useRef, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { api, retryAfterMinutes } from '../api/client'
import { t } from '../i18n'
import { Button, PinInput, TextField, type Currency } from '../ui'
import { amountSchema, cardLabel, pickCard, problemCode, pinSchema, useOwnCards } from './money'
import { CardsGate, MoneyDone, MoneyError } from './MoneyViews'

type Done = { amount: string; currency: Currency; last4: string }

// Mirrors TransferRequest: own card id, 16-digit card number, amount, 6-digit PIN.
const schema = z.object({
  fromCardId: z.string(),
  toCardNumber: z.string().transform((s) => s.replace(/[\s-]/g, ''))
    .pipe(z.string().regex(/^\d{16}$/, t('transfer.toInvalid'))),
  amount: amountSchema,
  pin: pinSchema,
})
type Input = z.input<typeof schema>
type Form = z.output<typeof schema>

export default function Transfer() {
  const { cards, reload, retry } = useOwnCards()
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<Done | null>(null)
  // One key per (card, recipient, amount), PIN left out like the server does: a resend of the same transfer (network error,
  // RESOURCE_BUSY, retyped PIN) reuses it, so it cannot move money twice. The server keeps a key only on success.
  const attempt = useRef<{ body: string; key: string } | null>(null)
  const { register, control, handleSubmit, reset, setError: setFieldError, setValue, formState: { errors, isSubmitting } } =
    useForm<Input, unknown, Form>({ resolver: zodResolver(schema), defaultValues: { fromCardId: '', toCardNumber: '', amount: '', pin: '' } })

  const fromCardId = useWatch({ control, name: 'fromCardId' })
  const from = Array.isArray(cards) ? pickCard(cards, fromCardId) : undefined

  const send = async (form: Form) => {
    setError(null)
    const card = Array.isArray(cards) ? pickCard(cards, form.fromCardId) : undefined
    if (!card?.id) return
    const body = { fromCardId: card.id, toCardNumber: form.toCardNumber, amount: form.amount, pin: form.pin }
    const same = `${body.fromCardId}|${body.toCardNumber}|${body.amount}`
    const key = attempt.current?.body === same ? attempt.current.key : crypto.randomUUID()
    attempt.current = { body: same, key }
    try {
      const { data, error: problem, response } = await api.POST('/api/transfer',
        { body, params: { header: { 'Idempotency-Key': key } } })
      const r = data?.data
      if (response.ok && r?.amount && r.currency) {
        attempt.current = null
        setDone({ amount: r.amount, currency: r.currency, last4: form.toCardNumber.slice(-4) })
        return
      }
      switch (problemCode(problem)) {
        case 'INVALID_PIN': setValue('pin', ''); setFieldError('pin', { message: t('money.pinWrong') }); return
        case 'NOT_FOUND': setError(t('transfer.notFound')); return // the recipient, or the source card if it was just closed
        case 'SAME_CARD': setFieldError('toCardNumber', { message: t('transfer.sameCard') }, { shouldFocus: true }); return
        case 'CURRENCY_MISMATCH': setFieldError('toCardNumber', { message: t('transfer.currencyMismatch') }, { shouldFocus: true }); return
        case 'INSUFFICIENT_BALANCE': setFieldError('amount', { message: t('transfer.insufficient') }, { shouldFocus: true }); return
        case 'PIN_LOCKED': setError(t('money.pinLocked', { minutes: retryAfterMinutes(response) ?? 15 })); return
        case 'PIN_NOT_SET': setError(t('money.pinNotSet')); return
        case 'LEGAL_ACCEPTANCE_REQUIRED': setError(t('money.legal')); return
        case 'RESOURCE_BUSY': setError(t('money.busy')); return
        default: setError(t('transfer.rejected'))
      }
    } catch {
      setError(t('transfer.failed')) // the key is kept: pressing Send again is a safe retry
    }
  }

  const again = () => {
    setDone(null)
    reset({ fromCardId: from ? String(from.id) : '', toCardNumber: '', amount: '', pin: '' })
    reload() // balances changed
  }

  if (done) {
    return <MoneyDone title={t('transfer.sent')} amount={done.amount} currency={done.currency}
      detail={t('transfer.sentTo', { last4: done.last4 })} again={again} againLabel={t('transfer.another')} />
  }

  return (
    <main className="page">
      <h1>{t('transfer.title')}</h1>
      <CardsGate cards={cards} retry={retry}>{(list) => (
        <form className="auth-form" onSubmit={(e) => void handleSubmit(send)(e)} noValidate>
          {error && <MoneyError error={error} from="/transfer" />}
          <div className="field">
            <label htmlFor="from" className="field-label">{t('money.from')}</label>
            <div className="field-control">
              <select id="from" {...register('fromCardId')}>
                {list.map((c) => <option key={c.id} value={String(c.id)}>{cardLabel(c)}</option>)}
              </select>
            </div>
          </div>
          <TextField label={t('transfer.to')} inputMode="numeric" autoComplete="off" placeholder="0000 0000 0000 0000"
            error={errors.toCardNumber?.message} {...register('toCardNumber')} />
          {/* shortcut: the fee rate is server config (FEE_COMMISSION_PERCENT) with no endpoint; show the number with T-129 */}
          <TextField label={t('money.amount')} inputMode="decimal" autoComplete="off" placeholder="0.00"
            suffix={from?.currency} hint={t('transfer.feeHint')} error={errors.amount?.message} {...register('amount')} />
          <Controller name="pin" control={control} render={({ field }) => (
            <PinInput ref={field.ref} label={t('money.pin')} value={field.value} onChange={field.onChange} error={errors.pin?.message} />
          )} />
          <Button type="submit" block loading={isSubmitting}>{t('transfer.submit')}</Button>
        </form>
      )}</CardsGate>
    </main>
  )
}
