import { useState } from 'react'
import { Link, Outlet } from 'react-router'
import { signOut } from '../auth/signOut'
import { t } from '../i18n'
import { Alert, Button } from '../ui'
import { Logo } from './Logo'

export default function AppShell() {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  const onSignOut = async () => {
    setBusy(true)
    setFailed(false)
    const ok = await signOut()
    if (!ok) { setFailed(true); setBusy(false) } // on success this shell unmounts
  }
  return (
    <div className="shell">
      <header className="shell-header">
        <Link to="/" className="shell-brand"><Logo /> {t('app.name')}</Link>
        <Button variant="ghost" size="sm" loading={busy} onClick={() => void onSignOut()}>
          {t('shell.signOut')}
        </Button>
      </header>
      {failed && <Alert tone="danger" title={t('shell.signOutFailed')} />}
      <Outlet />
    </div>
  )
}
