import { zodResolver } from '@hookform/resolvers/zod'
import { useId, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { Alert, Button, TextField } from '../ui'

type Category = components['schemas']['CreateProblemReportRequest']['category']
const CATEGORIES = ['TRANSACTION_FAILED', 'INCORRECT_AMOUNT_CHARGED', 'MISSING_TRANSACTION', 'TRANSFER_DELAYED',
  'CARD_NOT_WORKING', 'SECURITY_CONCERN'] as const satisfies readonly Category[]
const MAX_MESSAGE = 2000

// Mirrors CreateProblemReportRequest: a category, a message of at most 2000 characters, an optional reference up to 100.
const schema = z.object({
  category: z.enum(CATEGORIES, { error: t('report.categoryRequired') }),
  message: z.string().trim().min(1, t('report.messageRequired')).max(MAX_MESSAGE, t('signUp.tooLong', { max: MAX_MESSAGE })),
  referenceId: z.string().trim().max(100, t('signUp.tooLong', { max: 100 })).transform((s) => s || undefined),
})
type Input = z.input<typeof schema>
type Form = z.output<typeof schema>

/** Report a problem (POST /api/reports). Support answers by email; there is no list of own reports yet. */
export default function ReportProblem() {
  const [sent, setSent] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const messageId = useId()
  const { register, handleSubmit, control, formState: { errors, isSubmitting } } =
    useForm<Input, unknown, Form>({ resolver: zodResolver(schema), defaultValues: { category: undefined, message: '', referenceId: '' } })
  const length = useWatch({ control, name: 'message' })?.length ?? 0

  const send = async (form: Form) => {
    setError(null)
    try {
      const { data, response } = await api.POST('/api/reports', { body: form })
      if (response.ok) return setSent(data?.data?.id ?? 0)
      setError(t(response.status === 400 ? 'signUp.invalid' : 'report.failed'))
    } catch {
      setError(t('report.failed'))
    }
  }

  if (sent !== null) {
    return (
      <main className="page">
        <h1 ref={(el) => el?.focus()} tabIndex={-1}>{t('report.sent')}</h1>
        <p className="muted">{sent ? t('report.sentBody', { id: sent }) : t('report.sentBodyNoId')}</p>
        <Link className="btn btn-primary" to="/">{t('money.home')}</Link>
      </main>
    )
  }

  const describedBy = errors.message ? `${messageId}-error` : `${messageId}-count`
  return (
    <main className="page">
      <h1>{t('report.title')}</h1>
      <form className="auth-form" noValidate onSubmit={(e) => void handleSubmit(send)(e)}>
        {error && <Alert tone="danger" title={error} />}
        <div className={['field', errors.category && 'field-invalid'].filter(Boolean).join(' ')}>
          <label htmlFor="category" className="field-label">{t('report.category')}</label>
          <div className="field-control">
            <select id="category" aria-invalid={errors.category ? true : undefined}
              aria-describedby={errors.category ? 'category-error' : undefined} {...register('category')}>
              <option value="">{t('report.choose')}</option>
              {CATEGORIES.map((c) => <option key={c} value={c}>{t(`report.cat.${c}`)}</option>)}
            </select>
          </div>
          {errors.category && <p id="category-error" className="field-error">{errors.category.message}</p>}
        </div>
        <div className={['field', errors.message && 'field-invalid'].filter(Boolean).join(' ')}>
          <label htmlFor={messageId} className="field-label">{t('report.message')}</label>
          <div className="field-control field-control-area">
            <textarea id={messageId} rows={6} maxLength={MAX_MESSAGE} aria-invalid={errors.message ? true : undefined}
              aria-describedby={describedBy} {...register('message')} />
          </div>
          {errors.message
            ? <p id={`${messageId}-error`} className="field-error">{errors.message.message}</p>
            : <p id={`${messageId}-count`} className="field-hint">{t('report.count', { n: length, max: MAX_MESSAGE })}</p>}
        </div>
        <TextField label={t('report.reference')} hint={t('report.referenceHint')} autoComplete="off"
          error={errors.referenceId?.message} {...register('referenceId')} />
        <Button type="submit" block loading={isSubmitting}>{t('report.submit')}</Button>
      </form>
    </main>
  )
}
