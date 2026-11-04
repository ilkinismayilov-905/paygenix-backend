import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuthStatus } from '../auth/session'
import { t } from '../i18n'

function Checking() {
  return <main className="center-screen" aria-busy="true"><p className="muted">{t('auth.loading')}</p></main>
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

/** Sign-in and sign-up: a signed-in user goes straight to the app. */
export function PublicOnly() {
  const status = useAuthStatus()
  if (status === 'unknown') return <Checking />
  if (status === 'signed-in') return <Navigate to="/" replace />
  return <Outlet />
}
