import { Navigate, Outlet, useLocation } from 'react-router'
import { safeReturnPath } from '../auth/returnPath'
import { useAuthStatus } from '../auth/session'
import { t } from '../i18n'

function Checking() {
  return <main className="center-screen" aria-busy="true"><p className="muted" role="status">{t('auth.loading')}</p></main>
}

/** Signed-in area: waits for the startup refresh, then sends signed-out users to sign-in and back afterwards. */
export function RequireAuth() {
  const status = useAuthStatus()
  const location = useLocation()
  if (status === 'unknown') return <Checking />
  if (status === 'signed-out') {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname + location.search, expired: true }} />
  }
  return <Outlet />
}

/** Sign-in and sign-up: a signed-in user goes to the page they came for, or home. */
export function PublicOnly() {
  const status = useAuthStatus()
  const state = useLocation().state as { from?: string } | null
  if (status === 'unknown') return <Checking />
  if (status === 'signed-in') return <Navigate to={safeReturnPath(state?.from)} replace />
  return <Outlet />
}
