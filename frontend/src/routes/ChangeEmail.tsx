import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api, retryAfterMinutes } from '../api/client'
import { broadcastSignOut, signedOut } from '../auth/session'
import { t } from '../i18n'
import { Button, PinInput, TextField } from '../ui'
import { MoneyError } from './MoneyViews'
import { pinSchema, problemCode } from './money'

/**
 * Change email: new address + current PIN (POST /api/v1/profile/email) → the code sent to the new address and the one
 * sent to the current address (POST /api/v1/profile/email/confirm). The change signs out every device, this one too.
 */
type Step = 'start' | 'codes' | 'done'

const code = z.string().transform((v) => v.replace(/\s/g, '')).pipe(z.string().regex(/^\d{6}$/, t('signUp.codeInvalid')))
// Mirrors EmailChangeRequest: an address of at most 255 characters and a 6-digit PIN.
const startSchema = z.object({
  email: z.string().trim().toLowerCase().max(255, t('signIn.emailInvalid')).pipe(z.email(t('signIn.emailInvalid'))),
  pin: pinSchema,
})
const codesSchema = z.object({ otp: code, currentEmailOtp: code })
type StartInput = z.input<typeof startSchema>
type StartForm = z.output<typeof startSchema>

// The pending change is gone after these: back to the first step.
const RESTART = new Set(['OTP_EXPIRED', 'TOO_MANY_ATTEMPTS', 'EMAIL_TAKEN'])

export default function ChangeEmail() {
  const [step, setStep] = useState<Step>('start')
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (step === 'done') {
    return (
      <main className="page">
        <h1 ref={(el) => el?.focus()} tabIndex={-1}>{t('changeEmail.done')}</h1>
        <p className="muted">{t('changeEmail.doneBody', { email })}</p>
        {/* the server already ended every session; this only tells the tabs */}
        <Button onClick={() => { signedOut(); broadcastSignOut() }}>{t('changeEmail.signIn')}</Button>
      </main>
    )
  }

  return (
    <main className="page">
      {/* each step's first field takes focus (autoFocus), so after a restart focus is not left on <body> */}
      <h1>{t(step === 'start' ? 'changeEmail.title' : 'signUp.codeTitle')}</h1>
      {error && <MoneyError error={error} from="/settings/email" />}
      {step === 'start'
        ? <StartForm defaultEmail={email} onError={setError} onSent={(e) => { setError(null); setEmail(e); setStep('codes') }} />
        : <CodesForm email={email} onError={(message, restart) => { if (restart) setStep('start'); setError(message) }}
            onDone={() => setStep('done')} onBack={() => { setError(null); setStep('start') }} />}
      <p><Link to="/settings/profile">{t('changePin.back')}</Link></p>
    </main>
  )
}

function StartForm({ defaultEmail, onSent, onError }: { defaultEmail: string; onSent: (email: string) => void; onError: (e: string | null) => void }) {
  const { register, control, handleSubmit, setValue, setError, formState: { errors, isSubmitting } } =
    useForm<StartInput, unknown, StartForm>({ resolver: zodResolver(startSchema), defaultValues: { email: defaultEmail, pin: '' } })

  const send = async (form: StartForm) => {
    onError(null)
    try {
      const { error: problem, response } = await api.POST('/api/v1/profile/email', { body: form })
      if (response.ok) return onSent(form.email)
      const minutes = retryAfterMinutes(response)
      switch (problemCode(problem)) {
        case 'INVALID_PIN': setValue('pin', ''); return setError('pin', { message: t('money.pinWrong') }, { shouldFocus: true })
        case 'EMAIL_TAKEN': return setError('email', { message: t('changeEmail.taken') }, { shouldFocus: true })
        case 'VALIDATION_FAILED': return onError(t('signUp.invalid'))
        case 'PIN_NOT_SET': return onError(t('money.pinNotSet'))
        case 'PIN_LOCKED': return onError(t('money.pinLocked', { minutes: minutes ?? 15 }))
        case 'OTP_SEND_TOO_FREQUENT': return onError(t('changePin.wait', { minutes: minutes ?? 1 }))
        case 'EMAIL_CHANGE_LIMIT': return onError(t('changeEmail.limit', { hours: Math.max(1, Math.ceil((minutes ?? 1440) / 60)) }))
        default: return onError(t('signUp.failed'))
      }
    } catch {
      onError(t('signUp.failed'))
    }
  }

  return (
    <form className="auth-form" noValidate onSubmit={(e) => void handleSubmit(send)(e)}>
      <p className="muted">{t('changeEmail.intro')}</p>
      <TextField label={t('changeEmail.newEmail')} type="email" autoComplete="email" autoFocus
        error={errors.email?.message} {...register('email')} />
      <Controller name="pin" control={control} render={({ field }) => (
        <PinInput ref={field.ref} label={t('changeEmail.pin')} value={field.value} onChange={field.onChange} error={errors.pin?.message} />
      )} />
      <Button type="submit" block loading={isSubmitting}>{t('changeEmail.send')}</Button>
    </form>
  )
}

function CodesForm({ email, onDone, onError, onBack }:
  { email: string; onDone: () => void; onError: (e: string | null, restart?: boolean) => void; onBack: () => void }) {
  const { register, handleSubmit, setValue, formState: { errors, isSubmitting } } =
    useForm({ resolver: zodResolver(codesSchema), defaultValues: { otp: '', currentEmailOtp: '' } })

  const confirm = async (form: z.output<typeof codesSchema>) => {
    onError(null)
    try {
      const { error: problem, response } = await api.POST('/api/v1/profile/email/confirm', { body: form })
      if (response.ok) return onDone()
      const c = problemCode(problem)
      if (c === 'INVALID_OTP') {
        // one error for either code: the server does not say which was wrong
        setValue('otp', '')
        setValue('currentEmailOtp', '')
        return onError(t('changeEmail.codesWrong'))
      }
      if (c && RESTART.has(c)) {
        return onError(t(c === 'EMAIL_TAKEN' ? 'changeEmail.takenMeanwhile' : c === 'OTP_EXPIRED' ? 'changeEmail.expired' : 'changePin.tooMany'), true)
      }
      onError(t('signUp.failed'))
    } catch {
      onError(t('signUp.failed'))
    }
  }

  return (
    <form className="auth-form" noValidate onSubmit={(e) => void handleSubmit(confirm)(e)}>
      <p className="muted">{t('changeEmail.codesSent', { email })}</p>
      <TextField label={t('changeEmail.newCode', { email })} inputMode="numeric" autoComplete="one-time-code" autoFocus
        error={errors.otp?.message} {...register('otp')} />
      <TextField label={t('changeEmail.currentCode')} inputMode="numeric" autoComplete="off"
        error={errors.currentEmailOtp?.message} {...register('currentEmailOtp')} />
      <Button type="submit" block loading={isSubmitting}>{t('changeEmail.confirm')}</Button>
      <Button variant="ghost" block disabled={isSubmitting} onClick={onBack}>{t('changeEmail.startOver')}</Button>
    </form>
  )
}
