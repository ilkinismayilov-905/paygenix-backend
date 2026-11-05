import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useRef, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api, retryAfterMinutes, type Problem } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { Alert, Button, EmptyState, ErrorState, formatMoney, Money, PinInput, Skeleton, TextField, type Currency } from '../ui'

type Card = components['schemas']['CardResponse']
type Done = { amount: string; currency: Currency; last4: string }

// Mirrors TransferRequest: 16-digit card, 0.01+ with at most 2 decimals, 6-digit PIN.
const schema = z.object({
  fromCardId: z.string().min(1, t('transfer.fromRequired')),
  toCardNumber: z.string().transform((s) => s.replace(/[\s-]/g, ''))
    .pipe(z.string().regex(/^\d{16}$/, t('transfer.toInvalid'))),
  amount: z.string().transform((s) => s.trim().replace(',', '.'))
    .pipe(z.string().regex(/^\d{1,15}(\.\d{1,2})?$/, t('transfer.amountInvalid')).refine((s) => /[1-9]/.test(s), t('transfer.amountInvalid'))),
  pin: z.string().regex(/^\d{6}$/, t('transfer.pinInvalid')),
})
type Input = z.input<typeof schema>
type Form = z.output<typeof schema>

export default function Transfer() {
  const [cards, setCards] = useState<Card[] | null | 'failed'>(null)
  const [round, setRound] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<Done | null>(null)
  // One key per request body: a resend of the same body (network error, RESOURCE_BUSY) reuses it, so it cannot move money twice.
  const attempt = useRef<{ body: string; key: string } | null>(null)
  const { register, control, handleSubmit, getValues, reset, setError: setFieldError, setValue, formState: { errors, isSubmitting } } =
    useForm<Input, unknown, Form>({ resolver: zodResolver(schema), defaultValues: { fromCardId: '', toCardNumber: '', amount: '', pin: '' } })

  useEffect(() => {
    let live = true
    void api.GET('/api/cards/me')
      .then(({ data, response }) => {
        if (!live) return
        const list = response.ok && Array.isArray(data?.data) && data.data.every((c) => c.id && c.currency) ? data.data : null
        setCards(list ?? 'failed')
        if (list?.[0]) setValue('fromCardId', String(list[0].id))
      })
      .catch(() => { if (live) setCards('failed') })
    return () => { live = false }
  }, [round, setValue])

  const fromCardId = useWatch({ control, name: 'fromCardId' })
  const from = Array.isArray(cards) ? cards.find((c) => String(c.id) === fromCardId) : undefined

  const send = async (form: Form) => {
    setError(null)
    const body = { fromCardId: Number(form.fromCardId), toCardNumber: form.toCardNumber, amount: form.amount, pin: form.pin }
    const json = JSON.stringify(body)
    const key = attempt.current?.body === json ? attempt.current.key : crypto.randomUUID()
    attempt.current = { body: json, key }
    try {
      const { data, error: problem, response } = await api.POST('/api/transfer',
        { body, params: { header: { 'Idempotency-Key': key } } })
      const r = data?.data
      if (response.ok && r?.amount && r.currency) {
        attempt.current = null
        setDone({ amount: r.amount, currency: r.currency, last4: form.toCardNumber.slice(-4) })
        return
      }
      const code = (problem as Problem | undefined)?.code
      switch (code) {
        case 'INVALID_PIN': setValue('pin', ''); setFieldError('pin', { message: t('transfer.pinWrong') }); return
        case 'NOT_FOUND': setFieldError('toCardNumber', { message: t('transfer.toNotFound') }); return
        case 'SAME_CARD': setFieldError('toCardNumber', { message: t('transfer.sameCard') }); return
        case 'CURRENCY_MISMATCH': setFieldError('toCardNumber', { message: t('transfer.currencyMismatch') }); return
        case 'INSUFFICIENT_BALANCE': setFieldError('amount', { message: t('transfer.insufficient') }); return
        case 'PIN_LOCKED': setError(t('transfer.pinLocked', { minutes: retryAfterMinutes(response) ?? 15 })); return
        case 'PIN_NOT_SET': setError(t('transfer.pinNotSet')); return
        case 'LEGAL_ACCEPTANCE_REQUIRED': setError(t('transfer.legal')); return
        case 'RESOURCE_BUSY': setError(t('transfer.busy')); return
        default: setError(t('transfer.failed'))
      }
    } catch {
      setError(t('transfer.failed')) // the key is kept: pressing Send again is a safe retry
    }
  }

  const again = () => {
    setDone(null)
    reset({ fromCardId: getValues('fromCardId'), toCardNumber: '', amount: '', pin: '' })
    setRound((r) => r + 1) // balances changed
  }

  if (done) {
    return (
      <main className="page">
        <section className="transfer-done" aria-labelledby="sent">
          <h1 id="sent">{t('transfer.sent')}</h1>
          <Money amount={done.amount} currency={done.currency} size="xl" />
          <p className="muted">{t('transfer.sentTo', { last4: done.last4 })}</p>
          <div className="transfer-actions">
            <Link className="btn btn-primary" to="/">{t('transfer.home')}</Link>
            <Button variant="secondary" onClick={again}>{t('transfer.another')}</Button>
          </div>
        </section>
      </main>
    )
  }

  return (
    <main className="page">
      <h1>{t('transfer.title')}</h1>
      {cards === null ? (
        <div aria-busy="true"><Skeleton height="20rem" /></div>
      ) : cards === 'failed' ? (
        <ErrorState title={t('transfer.cardsFailed')}
          action={<Button variant="secondary" onClick={() => { setCards(null); setRound((r) => r + 1) }}>{t('error.retry')}</Button>} />
      ) : cards.length === 0 ? (
        <EmptyState title={t('home.noCards')} body={t('home.noCardsBody')} />
      ) : (
        <form className="auth-form" onSubmit={(e) => void handleSubmit(send)(e)} noValidate>
          {error && <Alert tone="danger" title={error} />}
          <div className="field">
            <label htmlFor="from" className="field-label">{t('transfer.from')}</label>
            <div className="field-control">
              <select id="from" {...register('fromCardId')}>
                {cards.map((c) => (
                  <option key={c.id} value={String(c.id)}>•••• {c.maskedNumber?.slice(-4)} · {c.amount ? formatMoney(c.amount, c.currency as Currency) : ''}</option>
                ))}
              </select>
            </div>
          </div>
          <TextField label={t('transfer.to')} inputMode="numeric" autoComplete="off" placeholder="0000 0000 0000 0000"
            error={errors.toCardNumber?.message} {...register('toCardNumber')} />
          {/* shortcut: the fee rate is server config (FEE_COMMISSION_PERCENT) with no endpoint; show the number once one exists */}
          <TextField label={t('transfer.amount')} inputMode="decimal" autoComplete="off" placeholder="0.00"
            suffix={from?.currency} hint={t('transfer.feeHint')} error={errors.amount?.message} {...register('amount')} />
          <Controller name="pin" control={control} render={({ field }) => (
            <PinInput label={t('transfer.pin')} value={field.value} onChange={field.onChange} error={errors.pin?.message} />
          )} />
          <Button type="submit" block loading={isSubmitting}>{t('transfer.submit')}</Button>
        </form>
      )}
    </main>
  )
}
