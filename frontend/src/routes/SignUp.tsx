import { zodResolver } from '@hookform/resolvers/zod'
import { Fragment, useState, type ReactNode } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate } from 'react-router'
import { z } from 'zod'
import { api, retryAfterMinutes, type Problem } from '../api/client'
import { signedIn } from '../auth/session'
import { t, type MessageKey } from '../i18n'
import { Alert, Button, TextField } from '../ui'
import { AgreeLine } from './legal'
import { PinStep } from './PinStep'
import { shownVersions, useLegalDocs } from './legalDocs'
import { Logo } from './Logo'

/**
 * Sign-up runs the backend onboarding flow one step at a time; the server accepts the steps only in this order.
 * shortcut: the flow lives in component state, so a reload starts over (the server flow expires in 30 min anyway).
 */
type Step = 'email' | 'code' | 'name' | 'username' | 'password' | 'pin' | 'pinRepeat'
type Flow = { email: string; flowId: string; token?: string }

const bytes = (s: string) => new TextEncoder().encode(s).length
// Codes after which this flow cannot continue: the user has to start again.
const FATAL = new Set(['FLOW_EXPIRED', 'FLOW_NOT_FOUND', 'TOO_MANY_ATTEMPTS', 'INVALID_STEP', 'UNAUTHORIZED', 'FORBIDDEN', 'ALREADY_TAKEN'])

type Failure = { message: string; fatal: boolean; code?: string }
type LegalDocs = ReturnType<typeof useLegalDocs>['docs']

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
    VALIDATION_FAILED: t('signUp.invalid'),
    LEGAL_VERSION_OUTDATED: t('signUp.legalChanged'),
  }
  const fatal = code !== undefined && FATAL.has(code)
  return { message: (code && messages[code]) ?? (fatal ? t('signUp.expired') : t('signUp.failed')), fatal, code }
}

/** Runs one API call; a thrown fetch (offline) becomes the generic failure. */
async function call<T>(run: () => Promise<{ data?: T; error?: unknown; response: Response }>) {
  try {
    const { data, error, response } = await run()
    return data !== undefined && response.ok ? { data } : { failure: failure(error, response), notFound: response.status === 404 }
  } catch {
    return { failure: failure(undefined, undefined) }
  }
}

export default function SignUp() {
  const [step, setStep] = useState<Step>('email')
  const [flow, setFlow] = useState<Flow | null>(null)
  const [error, setError] = useState<Failure | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  // Kept until the last step sends them together (the server takes password and PIN in one call).
  const [secret, setSecret] = useState({ password: '', pin: '' })
  const navigate = useNavigate()
  // Loaded once: the versions shown here are the ones the account accepts (T-121).
  const legal = useLegalDocs()
  const auth = () => ({ Authorization: `Bearer ${flow?.token ?? ''}` })
  const path = () => ({ path: { flowId: flow?.flowId ?? '' } })

  const go = (next: Step) => { setError(null); setNotice(null); setStep(next) }
  const restart = () => { setFlow(null); setSecret({ password: '', pin: '' }); go('email') }

  const startFlow = async (email: string) => {
    const r = await call(() => api.POST('/api/onboarding/email', { body: { email } }))
    if (r.failure) return setError(r.failure)
    const resent = step === 'code'
    setFlow({ email, flowId: r.data.data ?? '' })
    go('code')
    if (resent) setNotice(t('signUp.resent'))
  }

  const steps: Record<Step, ReactNode> = {
    email: <EmailStep onSubmit={startFlow} legal={legal.docs} />,
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
          go('password')
        }} />
    ),
    password: <PasswordStep onSubmit={(password) => { setSecret((x) => ({ ...x, password })); go('pin') }} />,
    pin: <PinStep label="signUp.pin" submit="signUp.continue" onSubmit={(pin) => { setSecret((x) => ({ ...x, pin })); go('pinRepeat') }} />,
    pinRepeat: (
      <PinStep label="signUp.pinRepeat" submit="signUp.create" onSubmit={async (repeat) => {
        const { password, pin } = secret
        if (repeat !== pin) {
          go('pin')
          return setError({ message: t('signUp.pinMismatch'), fatal: false })
        }
        const acceptedLegalDocuments = shownVersions(legal.docs)
        if (!acceptedLegalDocuments) {
          legal.reload()
          return setError({ message: t('signUp.failed'), fatal: false })
        }
        const r = await call(() => api.PUT('/api/onboarding/{flowId}/pin', { params: path(), headers: auth(), body: { pin, password, acceptedLegalDocuments } }))
        if (r.failure) {
          if (r.failure.code === 'VALIDATION_FAILED') go('password') // the PIN was checked here, so it is the password
          if (r.failure.code === 'LEGAL_VERSION_OUTDATED') legal.reload() // the new texts show under the alert
          return setError(r.failure)
        }
        // The account exists now; sign in with what was just typed so the user lands in the app.
        const login = await call(() => api.POST('/api/auth/login', { body: { email: flow?.email ?? '', password } }))
        if (login.data?.data?.token) signedIn(login.data.data.token) // PublicOnly then opens the app
        // The flow is finished on the server, so this screen cannot retry: sign-in takes over.
        else void navigate('/sign-in', { replace: true, state: { created: true } })
      }} />
    ),
  }

  const titles: Record<Step, MessageKey> = {
    email: 'signUp.title', code: 'signUp.codeTitle', name: 'signUp.nameTitle', username: 'signUp.usernameTitle',
    password: 'signUp.passwordTitle', pin: 'signUp.pinTitle', pinRepeat: 'signUp.pinRepeatTitle',
  }
  const order = Object.keys(titles)
  const index = order.indexOf(step) + 1

  return (
    <main className="auth">
      <div className="auth-panel">
        <Logo size={40} />
        <div>
          <p className="muted">{t('signUp.progress', { step: index, total: order.length })}</p>
          <h1>{t(titles[step])}</h1>
        </div>
        {error && (
          <Alert tone="danger" title={error.message}
            action={error.fatal ? <Button size="sm" variant="secondary" onClick={restart}>{t('signUp.startAgain')}</Button> : undefined} />
        )}
        {notice && <Alert tone="success" title={notice} />}
        {error?.code === 'LEGAL_VERSION_OUTDATED' && <AgreeLine docs={legal.docs} />}
        {/* keyed, so the name and username steps (same component) do not share form state */}
        <Fragment key={step}>{steps[step]}</Fragment>
        {step === 'email' && <p className="muted">{t('signUp.haveAccount')} <Link to="/sign-in">{t('signUp.signIn')}</Link></p>}
      </div>
    </main>
  )
}

const emailSchema = z.object({ email: z.string().trim().max(255).pipe(z.email({ error: t('signIn.emailInvalid') })) })

function EmailStep({ onSubmit, legal }: { onSubmit: (email: string) => Promise<void>; legal: LegalDocs }) {
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(emailSchema) })
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit((f) => onSubmit(f.email))}>
      <TextField label={t('signIn.email')} type="email" autoComplete="email" autoFocus error={errors.email?.message} {...register('email')} />
      <AgreeLine docs={legal} />
      <Button type="submit" block loading={isSubmitting}>{t('signUp.continue')}</Button>
    </form>
  )
}

const codeSchema = z.object({ otp: z.string().transform((v) => v.replace(/\s/g, '')).pipe(z.string().regex(/^\d{6}$/, t('signUp.codeInvalid'))) })

function CodeStep({ email, onSubmit, onResend, onBack }:
  { email: string; onSubmit: (otp: string) => Promise<void>; onResend: () => Promise<void>; onBack: () => void }) {
  const [resending, setResending] = useState(false)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(codeSchema) })
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit((f) => onSubmit(f.otp))}>
      {/* SEC-23: an email that already has an account gets a reminder instead of a code, and the page must not tell. */}
      <p className="muted">{t('signUp.codeSent', { email })}</p>
      <TextField label={t('signUp.code')} inputMode="numeric" autoComplete="one-time-code" autoFocus
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

// Mirrors PinRequest: password 8+ characters, not blank, and at most 72 UTF-8 bytes (bcrypt).
const passwordRules = [
  { key: 'signUp.ruleLength', ok: (p: string) => p.length >= 8 && p.trim() !== '' },
  { key: 'signUp.ruleBytes', ok: (p: string) => p.length > 0 && bytes(p) <= 72 },
] as const

function PasswordStep({ onSubmit }: { onSubmit: (password: string) => void }) {
  const [shown, setShown] = useState(false)
  const [tried, setTried] = useState(0) // submit attempts; re-keys the alert so each one is announced
  const { register, handleSubmit, control, setFocus } = useForm({ defaultValues: { password: '' } })
  const password = useWatch({ control, name: 'password' })
  const valid = passwordRules.every((r) => r.ok(password))
  const toggle = (
    <button type="button" className="link" aria-pressed={shown} onClick={() => setShown((v) => !v)}>
      {t(shown ? 'signUp.hide' : 'signUp.show')}
    </button>
  )
  // The checklist is the hint, so screen readers hear it with the field; it updates as the user types.
  const rules = (
    <ul className="rules">
      {passwordRules.map((r) => (
        <li key={r.key} className={r.ok(password) ? 'rule-ok' : tried > 0 ? 'rule-bad' : undefined}>
          <span aria-hidden="true">{r.ok(password) ? '✓' : '•'}</span> {t(r.key)}{' '}
          <span className="sr-only">{r.ok(password) ? t('signUp.ruleMet') : t('signUp.ruleNotMet')}</span>
        </li>
      ))}
    </ul>
  )
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit((f) => {
      setTried((n) => n + 1)
      if (valid) onSubmit(f.password)
      else setFocus('password')
    })}>
      <TextField label={t('signUp.password')} type={shown ? 'text' : 'password'} autoComplete="new-password" autoFocus
        suffix={toggle} hint={rules} aria-invalid={tried > 0 && !valid ? true : undefined} {...register('password')} />
      {tried > 0 && !valid && <p key={tried} role="alert" className="sr-only">{t('signUp.passwordRulesUnmet')}</p>}
      <Button type="submit" block>{t('signUp.continue')}</Button>
    </form>
  )
}

