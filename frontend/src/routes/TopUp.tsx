import { zodResolver } from '@hookform/resolvers/zod'
import { useRef, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { useSearchParams } from 'react-router'
import { z } from 'zod'
import { api, retryAfterMinutes } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, TextField } from '../ui'
import { amountSchema, cardLabel, pickCard, problemCode, useOwnCards } from './money'
import { CardsGate } from './MoneyViews'

// Mirrors TopUpRequest: own card, 1.00-10000.00.
const schema = z.object({
  fromCardId: z.string(),
  amount: amountSchema.refine((a) => Number(a) >= 1 && Number(a) <= 10000, t('topUp.range')),
})
type Input = z.input<typeof schema>
type Form = z.output<typeof schema>

/** Top up a card: POST /api/topups opens a Stripe Checkout (test mode) and the browser goes there. */
export default function TopUp() {
  const [params] = useSearchParams()
  const { cards, retry } = useOwnCards()
  const [error, setError] = useState<string | null>(null)
  // one key per (card, amount): a resend after a lost answer returns the same checkout
  const attempt = useRef<{ body: string; key: string } | null>(null)
  const { register, control, handleSubmit, formState: { errors, isSubmitting } } =
    useForm<Input, unknown, Form>({ resolver: zodResolver(schema), defaultValues: { fromCardId: params.get('card') ?? '', amount: '' } })
  const fromCardId = useWatch({ control, name: 'fromCardId' })
  const from = Array.isArray(cards) ? pickCard(cards, fromCardId) : undefined

  const start = async (form: Form) => {
    setError(null)
    const card = Array.isArray(cards) ? pickCard(cards, form.fromCardId) : undefined
    if (!card?.id) return
    const same = `${card.id}|${form.amount}`
    const key = attempt.current?.body === same ? attempt.current.key : crypto.randomUUID()
    attempt.current = { body: same, key }
    try {
      const { data, error: problem, response } = await api.POST('/api/topups', {
        body: { cardId: card.id, amount: form.amount },
        params: { header: { 'Idempotency-Key': key } },
      })
      const url = data?.data?.checkoutUrl
      if (response.ok && url) {
        attempt.current = null // coming back (Back, bfcache) starts a new top-up, not this finished one
        window.location.assign(url)
        return // the page stays busy until the browser leaves
      }
      switch (problemCode(problem)) {
        case 'TOP_UP_UNAVAILABLE': return setError(t('topUp.unavailable', { minutes: retryAfterMinutes(response) ?? 1 }))
        case 'NOT_FOUND': return setError(t('pay.cardGone'))
        case 'TOP_UP_FINISHED': attempt.current = null; return setError(t('topUp.finished'))
        case 'VALIDATION_FAILED': return setError(t('topUp.range'))
        default: return setError(t('topUp.failed'))
      }
    } catch {
      setError(t('topUp.failed'))
    }
  }

  return (
    <main className="page">
      <h1>{t('topUp.title')}</h1>
      <CardsGate cards={cards} retry={retry}>{(list) => (
        <form className="auth-form" onSubmit={(e) => void handleSubmit(start)(e)} noValidate>
          {error && <Alert tone="danger" title={error} />}
          <div className="field">
            <label htmlFor="to" className="field-label">{t('topUp.card')}</label>
            <div className="field-control">
              {/* shows the card really used (pickCard): an unset or closed choice still shows a card */}
              <Controller name="fromCardId" control={control} render={({ field }) => (
                <select id="to" ref={field.ref} name={field.name} onBlur={field.onBlur} onChange={field.onChange}
                  value={from ? String(from.id) : ''}>
                  {list.map((c) => <option key={c.id} value={String(c.id)}>{cardLabel(c)}</option>)}
                </select>
              )} />
            </div>
          </div>
          <TextField label={t('money.amount')} inputMode="decimal" autoComplete="off" placeholder="0.00" suffix={from?.currency}
            hint={t('topUp.hint')} error={errors.amount?.message} {...register('amount')} />
          <Button type="submit" block loading={isSubmitting}>{t('topUp.submit')}</Button>
          <p className="muted">{t('topUp.testCard')}</p>
        </form>
      )}</CardsGate>
    </main>
  )
}
