import { useEffect, useState } from 'react'
import { Link, Outlet } from 'react-router'
import { signOut } from '../auth/signOut'
import { t } from '../i18n'
import { Alert, Button } from '../ui'
import { AcceptLegal } from './legal'
import { loadMe, type LegalRef } from './legalDocs'
import { Logo } from './Logo'

export type ShellContext = { admin: boolean }

export default function AppShell() {
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  // T-121: null until checked; if the check fails the app opens anyway (the backend still blocks money moves)
  const [pending, setPending] = useState<LegalRef[] | null>(null)
  // UI only: the admin API checks the role on every call
  const [admin, setAdmin] = useState(false)
  useEffect(() => {
    let live = true
    void loadMe().then((me) => { if (live) { setPending(me?.pending ?? []); setAdmin(me?.admin ?? false) } })
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
        <Link to="/" className="shell-brand"><Logo /> <span className="shell-brand-name">{t('app.name')}</span></Link>
        <nav className="shell-nav">
          <Link className="btn btn-ghost btn-sm" to="/cards">{t('shell.cards')}</Link>
          {admin && <Link className="btn btn-ghost btn-sm" to="/admin/kyc">{t('shell.admin')}</Link>}
          <Link className="btn btn-ghost btn-sm" to="/settings/profile">{t('shell.profile')}</Link>
          <Button variant="ghost" size="sm" loading={busy} onClick={() => void onSignOut()}>
            {t('shell.signOut')}
          </Button>
        </nav>
      </header>
      {failed && <Alert tone="danger" title={t('shell.signOutFailed')} />}
      {pending === null ? null : pending.length > 0 ? <AcceptLegal pending={pending} onDone={setPending} />
        : <Outlet context={{ admin } satisfies ShellContext} />}
    </div>
  )
}
