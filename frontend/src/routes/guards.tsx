import { useState } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { safeReturnPath } from '../auth/returnPath'
import { refresh, sessionExpired, signedOutByUser, useAuthStatus } from '../auth/session'
import { t } from '../i18n'
import { Alert, Button } from '../ui'

function Checking() {
  return <main className="center-screen" aria-busy="true"><p className="muted" role="status">{t('auth.loading')}</p></main>
}

/** The session check could not reach the server: nothing is decided yet, so offer a retry instead of signing out. */
function Unavailable() {
  const [busy, setBusy] = useState(false)
  const retry = () => { setBusy(true); void refresh().finally(() => setBusy(false)) }
  return (
    <main className="center-screen">
      <h1 className="sr-only">{t('auth.unavailableTitle')}</h1>
      <Alert tone="warning" title={t('auth.unavailable')}
        action={<Button size="sm" variant="secondary" loading={busy} onClick={retry}>{t('error.retry')}</Button>} />
    </main>
  )
}

/**
 * Signed-in area: waits for the startup refresh, then sends signed-out users to sign-in and back afterwards —
 * unless they signed out themselves: then the next sign-in starts at home, not on the page they left.
 */
export function RequireAuth() {
  const status = useAuthStatus()
  const location = useLocation()
  if (status === 'unknown') return <Checking />
  if (status === 'unavailable') return <Unavailable />
  if (status === 'signed-out') {
    const from = signedOutByUser() ? undefined : location.pathname + location.search
    return <Navigate to="/sign-in" replace state={{ from, expired: sessionExpired() }} />
  }
  return <Outlet />
}

/** Sign-in and sign-up: a signed-in user goes to the page they came for, or home. */
export function PublicOnly() {
  const status = useAuthStatus()
  const state = useLocation().state as { from?: string } | null
  if (status === 'unknown') return <Checking />
  if (status === 'unavailable') return <Unavailable />
  if (status === 'signed-in') return <Navigate to={safeReturnPath(state?.from)} replace />
  return <Outlet />
}
