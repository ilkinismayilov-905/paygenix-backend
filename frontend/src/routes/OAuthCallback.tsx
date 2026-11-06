import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { api } from '../api/client'
import { signedIn, useAuthStatus } from '../auth/session'
import { t, type MessageKey } from '../i18n'
import { Alert } from '../ui'

const errors: Record<string, MessageKey> = {
  email_not_verified: 'oauth.emailNotVerified',
  account_exists: 'oauth.accountExists',
  account_locked: 'oauth.accountLocked',
}

// The code works once. React StrictMode runs effects twice in development, so both runs share one exchange.
const exchanges = new Map<string, Promise<string | null>>()

function exchange(code: string): Promise<string | null> {
  let p = exchanges.get(code)
  if (!p) {
    p = api.POST('/api/v1/auth/oauth/exchange', { body: { code } })
      .then(({ data }) => data?.data?.token ?? null)
      .catch(() => null)
    exchanges.set(code, p)
  }
  return p
}

export default function OAuthCallback() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const code = params.get('code')
  const status = useAuthStatus()
  const [failed, setFailed] = useState<MessageKey | null>(code ? null : errors[params.get('error') ?? ''] ?? 'oauth.failed')

  useEffect(() => {
    // Wait for the startup refresh: its result (an old session, or clearing a dead cookie) must not land after this sign-in.
    if (!code || status === 'unknown') return
    // Both StrictMode runs may resolve; signing in and navigating twice is harmless.
    void exchange(code).then((token) => {
      if (token) { signedIn(token); navigate('/', { replace: true }) }
      else setFailed('oauth.failed')
    })
  }, [code, navigate, status])

  return (
    <main className="auth">
      <div className="auth-panel">
        {failed ? (
          <>
            <Alert tone="danger" title={t(failed)} />
            <Link to="/sign-in">{t('oauth.back')}</Link>
          </>
        ) : (
          <p className="muted" aria-busy="true">{t('oauth.working')}</p>
        )}
      </div>
    </main>
  )
}
