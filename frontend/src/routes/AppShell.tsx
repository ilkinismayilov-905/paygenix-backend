import { useEffect, useState } from 'react'
import { Link, Outlet } from 'react-router'
import { signOut } from '../auth/signOut'
import { t } from '../i18n'
import { Alert, Button } from '../ui'
import { AcceptLegal } from './legal'
import { loadPendingLegal, type LegalRef } from './legalDocs'
import { Logo } from './Logo'

export default function AppShell() {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  // T-121: null until checked; if the check fails the app opens anyway (the backend still blocks money moves)
  const [pending, setPending] = useState<LegalRef[] | null>(null)
  useEffect(() => {
    let live = true
    void loadPendingLegal().then((p) => { if (live) setPending(p ?? []) })
    return () => { live = false }
  }, [])
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
        <nav className="shell-nav">
          <Link className="btn btn-ghost btn-sm" to="/settings/profile">{t('shell.profile')}</Link>
          <Button variant="ghost" size="sm" loading={busy} onClick={() => void onSignOut()}>
            {t('shell.signOut')}
          </Button>
        </nav>
      </header>
      {failed && <Alert tone="danger" title={t('shell.signOutFailed')} />}
      {pending === null ? null : pending.length > 0 ? <AcceptLegal pending={pending} onDone={setPending} /> : <Outlet />}
    </div>
  )
}
