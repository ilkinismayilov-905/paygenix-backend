import { zodResolver } from '@hookform/resolvers/zod'
import { Fragment, useEffect, useState, type ReactNode } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api, retryAfterMinutes, type Problem } from '../api/client'
import { signedIn } from '../auth/session'
import { t, type MessageKey } from '../i18n'
import { Alert, Button, Dialog, PinInput, TextField } from '../ui'
import { Logo } from './Logo'

/**
 * Sign-up runs the backend onboarding flow one step at a time; the server accepts the steps only in this order.
 * shortcut: the flow lives in component state, so a reload starts over (the server flow expires in 30 min anyway).
 */
type Step = 'email' | 'code' | 'name' | 'username' | 'secure'
type Flow = { email: string; flowId: string; token?: string }

const bytes = (s: string) => new TextEncoder().encode(s).length
// Codes after which this flow cannot continue: the user has to start again.
const FATAL = new Set(['FLOW_EXPIRED', 'FLOW_NOT_FOUND', 'TOO_MANY_ATTEMPTS', 'INVALID_STEP', 'UNAUTHORIZED', 'ALREADY_TAKEN'])

type Failure = { message: string; fatal: boolean }

function failure(problem: unknown, res: Response | undefined): Failure {
  const code = (problem as Problem | undefined)?.code
  const minutes = res ? retryAfterMinutes(res) : null
  const messages: Record<string, string> = {
    OTP_SEND_TOO_FREQUENT: t('signUp.sendTooFrequent'),
    INVALID_OTP: t('signUp.codeWrong'),
    OTP_EXPIRED: t('signUp.codeExpired'),
    USERNAME_TAKEN: t('signUp.usernameTaken'),
    USERNAME_CHECK_LIMIT: t('signUp.usernameLimit', { minutes: minutes ?? 15 }),
    ALREADY_TAKEN: t('signUp.alreadyTaken'),
    TOO_MANY_ATTEMPTS: t('signUp.tooManyCodes'),
  }
  const fatal = code !== undefined && FATAL.has(code)
  return { message: (code && messages[code]) ?? (fatal ? t('signUp.expired') : t('signUp.failed')), fatal }
}

/** Runs one API call; a thrown fetch (offline) becomes the generic failure. */
async function call<T>(run: () => Promise<{ data?: T; error?: unknown; response: Response }>) {
  try {
    const { data, error, response } = await run()
    return data !== undefined && response.ok ? { data } : { failure: failure(error, response) }
  } catch {
    return { failure: failure(undefined, undefined) }
  }
}

export default function SignUp() {
  const [step, setStep] = useState<Step>('email')
  const [flow, setFlow] = useState<Flow | null>(null)
  const [error, setError] = useState<Failure | null>(null)
  const auth = () => ({ Authorization: `Bearer ${flow?.token ?? ''}` })
  const path = () => ({ path: { flowId: flow?.flowId ?? '' } })

  const go = (next: Step) => { setError(null); setStep(next) }
  const restart = () => { setFlow(null); go('email') }

  const startFlow = async (email: string) => {
    const r = await call(() => api.POST('/api/onboarding/email', { body: { email } }))
    if (r.failure) return setError(r.failure)
    setFlow({ email, flowId: r.data.data ?? '' })
    go('code')
  }

  const steps: Record<Step, ReactNode> = {
    email: <EmailStep onSubmit={startFlow} />,
    code: (
      <CodeStep email={flow?.email ?? ''} onBack={restart} onResend={() => startFlow(flow?.email ?? '')}
        onSubmit={async (otp) => {
          const r = await call(() => api.POST('/api/onboarding/{flowId}/otp', { params: path(), body: { otp } }))
          if (r.failure) return setError(r.failure)
          setFlow((f) => f && { ...f, token: r.data.data ?? '' })
          go('name')
        }} />
    ),
    name: (
      <TextStep field="fullName" label="signUp.fullName" autoComplete="name" max={100}
        onSubmit={async (fullName) => {
          const r = await call(() => api.PUT('/api/onboarding/{flowId}/full-name', { params: path(), headers: auth(), body: { fullName } }))
          if (r.failure) return setError(r.failure)
          go('username')
        }} />
    ),
    username: (
      <TextStep field="username" label="signUp.username" hint="signUp.usernameHint" autoComplete="username" max={30}
        pattern={/^[A-Za-z0-9._]{3,30}$/}
        onSubmit={async (username) => {
          const r = await call(() => api.PUT('/api/onboarding/{flowId}/username', { params: path(), headers: auth(), body: { username } }))
          if (r.failure) return setError(r.failure)
          go('secure')
        }} />
    ),
    secure: (
      <SecureStep onSubmit={async ({ password, pin }) => {
        const r = await call(() => api.PUT('/api/onboarding/{flowId}/pin', { params: path(), headers: auth(), body: { pin, password } }))
        if (r.failure) return setError(r.failure)
        // The account exists now; sign in with what was just typed so the user lands in the app.
        const login = await call(() => api.POST('/api/auth/login', { body: { email: flow?.email ?? '', password } }))
        if (login.data?.data?.token) signedIn(login.data.data.token) // PublicOnly then opens the app
        else setError({ message: t('signUp.createdSignIn'), fatal: false })
      }} />
    ),
  }

  const titles: Record<Step, MessageKey> = {
    email: 'signUp.title', code: 'signUp.codeTitle', name: 'signUp.nameTitle', username: 'signUp.usernameTitle', secure: 'signUp.secureTitle',
  }
  const index = Object.keys(titles).indexOf(step) + 1

  return (
    <main className="auth">
      <div className="auth-panel">
        <Logo size={40} />
        <div>
          <p className="muted">{t('signUp.progress', { step: index, total: 5 })}</p>
          <h1>{t(titles[step])}</h1>
        </div>
        {error && (
          <Alert tone="danger" title={error.message}
            action={error.fatal ? <Button size="sm" variant="secondary" onClick={restart}>{t('signUp.startAgain')}</Button> : undefined} />
        )}
        {/* keyed, so the name and username steps (same component) do not share form state */}
        <Fragment key={step}>{steps[step]}</Fragment>
        {step === 'email' && <p className="muted">{t('signUp.haveAccount')} <Link to="/sign-in">{t('signUp.signIn')}</Link></p>}
      </div>
    </main>
  )
}

const emailSchema = z.object({ email: z.string().trim().max(255).pipe(z.email({ error: t('signIn.emailInvalid') })) })

function EmailStep({ onSubmit }: { onSubmit: (email: string) => Promise<void> }) {
  const [doc, setDoc] = useState<'terms' | 'privacy' | null>(null)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(emailSchema) })
  return (
    <>
      <form className="auth-form" noValidate onSubmit={handleSubmit((f) => onSubmit(f.email))}>
        <TextField label={t('signIn.email')} type="email" autoComplete="email" autoFocus error={errors.email?.message} {...register('email')} />
        <p className="muted">
          {t('signUp.agree')} <button type="button" className="link" onClick={() => setDoc('terms')}>{t('signUp.terms')}</button>
          {' '}{t('signUp.and')}{' '}<button type="button" className="link" onClick={() => setDoc('privacy')}>{t('signUp.privacy')}</button>.
        </p>
        <Button type="submit" block loading={isSubmitting}>{t('signUp.continue')}</Button>
      </form>
      {doc && <LegalDialog type={doc} onClose={() => setDoc(null)} />}
    </>
  )
}

function LegalDialog({ type, onClose }: { type: 'terms' | 'privacy'; onClose: () => void }) {
  const [text, setText] = useState<string | null>(null)
  useEffect(() => {
    let live = true
    void call(() => api.GET(type === 'terms' ? '/api/legal/terms' : '/api/legal/privacy'))
      .then((r) => { if (live) setText(r.data?.data?.content ?? t('signUp.legalMissing')) })
    return () => { live = false }
  }, [type])
  return (
    <Dialog open onClose={onClose} title={t(type === 'terms' ? 'signUp.terms' : 'signUp.privacy')}
      actions={<Button variant="secondary" onClick={onClose}>{t('signUp.close')}</Button>}>
      <p className="legal-text">{text ?? t('auth.loading')}</p>
    </Dialog>
  )
}

const codeSchema = z.object({ otp: z.string().trim().regex(/^\d{6}$/, t('signUp.codeInvalid')) })

function CodeStep({ email, onSubmit, onResend, onBack }:
  { email: string; onSubmit: (otp: string) => Promise<void>; onResend: () => Promise<void>; onBack: () => void }) {
  const [resending, setResending] = useState(false)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(codeSchema) })
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit((f) => onSubmit(f.otp))}>
      {/* SEC-23: an email that already has an account gets a reminder instead of a code, and the page must not tell. */}
      <p className="muted">{t('signUp.codeSent', { email })}</p>
      <TextField label={t('signUp.code')} inputMode="numeric" autoComplete="one-time-code" maxLength={6} autoFocus
        error={errors.otp?.message} {...register('otp')} />
      <Button type="submit" block loading={isSubmitting}>{t('signUp.continue')}</Button>
      <Button variant="ghost" block loading={resending}
        onClick={() => { setResending(true); void onResend().finally(() => setResending(false)) }}>{t('signUp.resend')}</Button>
      <Button variant="ghost" block onClick={onBack}>{t('signUp.otherEmail')}</Button>
    </form>
  )
}

function TextStep({ field, label, hint, autoComplete, max, pattern, onSubmit }: {
  field: string; label: MessageKey; hint?: MessageKey; autoComplete: string; max: number; pattern?: RegExp
  onSubmit: (value: string) => Promise<void>
}) {
  const schema = z.object({
    value: z.string().trim().min(1, t('signUp.required')).max(max, t('signUp.tooLong', { max }))
      .refine((v) => !pattern || pattern.test(v), hint ? t(hint) : t('signUp.required')),
  })
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(schema) })
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit((f) => onSubmit(f.value))}>
      <TextField id={field} label={t(label)} hint={hint && t(hint)} autoComplete={autoComplete} autoFocus
        error={errors.value?.message} {...register('value')} />
      <Button type="submit" block loading={isSubmitting}>{t('signUp.continue')}</Button>
    </form>
  )
}

// Mirrors PinRequest: password 8–72 characters and at most 72 UTF-8 bytes (bcrypt), PIN 6 digits.
const secureSchema = z.object({
  password: z.string().min(8, t('signUp.passwordShort')).refine((p) => bytes(p) <= 72, t('signIn.passwordTooLong')),
  confirm: z.string(),
  pin: z.string().regex(/^\d{6}$/, t('signUp.pinInvalid')),
  pinConfirm: z.string(),
}).refine((f) => f.password === f.confirm, { path: ['confirm'], message: t('signUp.passwordMismatch') })
  .refine((f) => f.pin === f.pinConfirm, { path: ['pinConfirm'], message: t('signUp.pinMismatch') })

function SecureStep({ onSubmit }: { onSubmit: (f: { password: string; pin: string }) => Promise<void> }) {
  const { register, control, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(secureSchema), defaultValues: { password: '', confirm: '', pin: '', pinConfirm: '' },
  })
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit(onSubmit)}>
      <TextField label={t('signUp.password')} type="password" autoComplete="new-password" autoFocus hint={t('signUp.passwordHint')}
        error={errors.password?.message} {...register('password')} />
      <TextField label={t('signUp.passwordConfirm')} type="password" autoComplete="new-password"
        error={errors.confirm?.message} {...register('confirm')} />
      <Controller control={control} name="pin" render={({ field }) => (
        <PinInput label={t('signUp.pin')} value={field.value} onChange={field.onChange} error={errors.pin?.message} />
      )} />
      <Controller control={control} name="pinConfirm" render={({ field }) => (
        <PinInput label={t('signUp.pinConfirm')} value={field.value} onChange={field.onChange} error={errors.pinConfirm?.message} />
      )} />
      <p className="muted">{t('signUp.pinHint')}</p>
      <Button type="submit" block loading={isSubmitting}>{t('signUp.create')}</Button>
    </form>
  )
}
