import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api, retryAfterMinutes } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, TextField } from '../ui'
import { problemCode } from './money'
import { PinStep } from './PinStep'

/** Change PIN: email code → new PIN → repeat (POST /api/change-pin, /{flowId}/otp, /{flowId}/pin). A reload starts over. */
type Step = 'start' | 'code' | 'pin' | 'pinRepeat' | 'done'

// After these the flow cannot continue: back to the start with a new code.
// A missing or foreign flow is a plain 404 NOT_FOUND (NotFoundException), not FLOW_NOT_FOUND.
const RESTART = new Set(['FLOW_EXPIRED', 'NOT_FOUND', 'TOO_MANY_ATTEMPTS', 'INVALID_STEP'])

function message(code: string | undefined, res?: Response): string {
  switch (code) {
    case 'OTP_SEND_TOO_FREQUENT': return t('changePin.wait', { minutes: (res && retryAfterMinutes(res)) ?? 1 })
    case 'INVALID_OTP': return t('signUp.codeWrong')
    case 'OTP_EXPIRED': return t('signUp.codeExpired')
    case 'TOO_MANY_ATTEMPTS': return t('changePin.tooMany')
    case 'PIN_UNCHANGED': return t('changePin.unchanged')
    case 'PIN_MISMATCH': return t('signUp.pinMismatch')
    case 'VALIDATION_FAILED': return t('signUp.pinInvalid')
    default: return code && RESTART.has(code) ? t('changePin.expired') : t('signUp.failed')
  }
}

/** One call; offline becomes the generic failure. */
async function call(run: () => Promise<{ data?: unknown; error?: unknown; response: Response }>) {
  try {
    const { data, error, response } = await run()
    return response.ok ? { data } : { code: problemCode(error), error: message(problemCode(error), response) }
  } catch {
    return { error: message(undefined) }
  }
}

const codeSchema = z.object({ otp: z.string().transform((v) => v.replace(/\s/g, '')).pipe(z.string().regex(/^\d{6}$/, t('signUp.codeInvalid'))) })

export default function ChangePin() {
  const [step, setStep] = useState<Step>('start')
  const [flowId, setFlowId] = useState('')
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [sending, setSending] = useState(false)
  const [resent, setResent] = useState(false)
  const path = { path: { flowId } }

  const send = async () => {
    setError(null)
    setResent(false)
    setSending(true)
    const r = await call(() => api.POST('/api/change-pin'))
    setSending(false)
    const id = (r.data as { data?: string } | undefined)?.data
    if (r.error || !id) return setError(r.error ?? message(undefined))
    setResent(step === 'code')
    setFlowId(id)
    setStep('code')
  }

  const fail = (r: { code?: string; error: string }) => {
    if (r.code && RESTART.has(r.code)) setStep('start')
    setError(r.error)
  }

  if (step === 'done') {
    return (
      <main className="page">
        <h1 ref={(el) => el?.focus()} tabIndex={-1}>{t('changePin.done')}</h1>
        <Link className="btn btn-primary" to="/settings/profile">{t('changePin.back')}</Link>
      </main>
    )
  }

  const titles = { start: 'changePin.title', code: 'signUp.codeTitle', pin: 'changePin.newTitle', pinRepeat: 'signUp.pinRepeatTitle' } as const
  return (
    <main className="page">
      {/* after a restart the pressed button is gone: the heading takes focus, not <body> */}
      <h1 ref={step === 'start' && error ? (el) => el?.focus() : undefined} tabIndex={-1}>{t(titles[step])}</h1>
      {error && <Alert tone="danger" title={error} />}
      {step === 'start' && (
        <div className="auth-form">
          <p className="muted">{t('changePin.intro')}</p>
          <Button block loading={sending} onClick={() => void send()}>{t('changePin.send')}</Button>
        </div>
      )}
      {step === 'code' && <>
        {resent && <Alert tone="success" title={t('changePin.resent')} />}
        <CodeForm key={flowId} sending={sending} onResend={send} onSubmit={async (otp) => {
          setError(null)
          const r = await call(() => api.POST('/api/change-pin/{flowId}/otp', { params: path, body: { otp } }))
          if (r.error) return fail(r)
          setStep('pin')
        }} />
      </>}
      {step === 'pin' && (
        <PinStep key="pin" label="changePin.newPin" submit="signUp.continue" onSubmit={(p) => { setError(null); setPin(p); setStep('pinRepeat') }} />
      )}
      {step === 'pinRepeat' && (
        <PinStep key="repeat" label="signUp.pinRepeat" submit="changePin.submit" onSubmit={async (repeat) => {
          setError(null)
          if (repeat !== pin) { setStep('pin'); return setError(t('signUp.pinMismatch')) }
          const r = await call(() => api.POST('/api/change-pin/{flowId}/pin', { params: path, body: { newPin: pin, reEnterNewPin: repeat } }))
          if (!r.error) return setStep('done')
          if (r.code === 'PIN_UNCHANGED' || r.code === 'VALIDATION_FAILED' || r.code === 'PIN_MISMATCH') setStep('pin')
          fail(r)
        }} />
      )}
    </main>
  )
}

function CodeForm({ sending, onSubmit, onResend }: { sending: boolean; onSubmit: (otp: string) => Promise<void>; onResend: () => Promise<void> }) {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(codeSchema) })
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit((f) => onSubmit(f.otp))}>
      <p className="muted">{t('changePin.codeSent')}</p>
      <TextField label={t('signUp.code')} inputMode="numeric" autoComplete="one-time-code" autoFocus
        error={errors.otp?.message} {...register('otp')} />
      <Button type="submit" block loading={isSubmitting}>{t('signUp.continue')}</Button>
      <Button variant="ghost" block loading={sending} disabled={isSubmitting} onClick={() => void onResend()}>{t('signUp.resend')}</Button>
    </form>
  )
}
