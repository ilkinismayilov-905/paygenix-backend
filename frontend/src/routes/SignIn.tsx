import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useLocation } from 'react-router'
import { z } from 'zod'
import { api, retryAfterMinutes } from '../api/client'
import { signedIn } from '../auth/session'
import { t } from '../i18n'
import { Alert, Button, TextField } from '../ui'
import { Logo } from './Logo'

// Mirrors LoginRequest: valid email up to 255. Password up to 72 UTF-8 bytes, bcrypt's real limit (letters like ə take 2).
const schema = z.object({
  email: z.string().trim().max(255).pipe(z.email({ error: t('signIn.emailInvalid') })),
  password: z.string().min(1, t('signIn.passwordRequired'))
    .refine((p) => new TextEncoder().encode(p).length <= 72, t('signIn.passwordTooLong')),
})
type Form = z.infer<typeof schema>

function errorMessage(code: string | undefined, res: Response): string {
  switch (code) {
    case 'UNAUTHORIZED': return t('signIn.wrong')
    case 'ACCOUNT_LOCKED': return t('signIn.accountLocked')
    case 'LOGIN_LOCKED': return t('signIn.tooMany', { minutes: retryAfterMinutes(res) ?? 15 })
    default: return t('signIn.failed')
  }
}

export default function SignIn() {
  const state = useLocation().state as { from?: string; expired?: boolean } | null
  const [error, setError] = useState<string | null>(null)
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<Form>({ resolver: zodResolver(schema) })

  const onSubmit = handleSubmit(async (form) => {
    setError(null)
    try {
      const { data, error: problem, response } = await api.POST('/api/auth/login', { body: form })
      if (data?.data?.token) {
        signedIn(data.data.token) // PublicOnly then redirects to where the user was going
        return
      }
      setError(errorMessage((problem as { code?: string } | undefined)?.code, response))
    } catch {
      setError(t('signIn.failed'))
    }
  })

  return (
    <main className="auth">
      <div className="auth-panel">
        <Logo size={40} />
        <h1>{t('signIn.title')}</h1>
        {error ? <Alert tone="danger" title={error} /> : state?.expired && <Alert title={t('signIn.expired')} />}
        <form className="auth-form" onSubmit={onSubmit} noValidate>
          <TextField label={t('signIn.email')} type="email" autoComplete="email" autoFocus
            error={errors.email?.message} {...register('email')} />
          <TextField label={t('signIn.password')} type="password" autoComplete="current-password"
            error={errors.password?.message} {...register('password')} />
          <Button type="submit" block loading={isSubmitting}>{t('signIn.submit')}</Button>
        </form>
        <p className="auth-or"><span>{t('signIn.or')}</span></p>
        {/* A full-page navigation: Spring runs the Google redirect and returns to /oauth2/callback. */}
        <a className="btn btn-secondary btn-block" href="/oauth2/authorization/google">{t('signIn.google')}</a>
      </div>
    </main>
  )
}
