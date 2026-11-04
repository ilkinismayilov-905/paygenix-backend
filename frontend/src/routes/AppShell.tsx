import { useState } from 'react'
import { Link, Outlet } from 'react-router'
import { signOut } from '../auth/signOut'
import { t } from '../i18n'
import { Button } from '../ui'
import { Logo } from './Logo'

export default function AppShell() {
  const [busy, setBusy] = useState(false)
  return (
    <div className="shell">
      <header className="shell-header">
        <Link to="/" className="shell-brand"><Logo /> {t('app.name')}</Link>
        <Button variant="ghost" size="sm" loading={busy} onClick={() => { setBusy(true); void signOut() }}>
          {t('shell.signOut')}
        </Button>
      </header>
      <Outlet />
    </div>
  )
}
