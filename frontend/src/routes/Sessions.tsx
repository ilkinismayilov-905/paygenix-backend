import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { broadcastSignOut, signedOut } from '../auth/session'
import { signOut } from '../auth/signOut'
import { t } from '../i18n'
import { Alert, Button, Dialog, ErrorState, List, ListRow, Skeleton } from '../ui'
import { deviceLabel } from './device'

type Session = components['schemas']['SessionResponse']

const when = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })
const formatWhen = (iso?: string) => (iso && !Number.isNaN(Date.parse(iso)) ? when.format(new Date(iso)) : '')

/** Signed-in devices (GET /api/auth/sessions): sign one out, or every device including this one. */
export default function Sessions() {
  const [sessions, setSessions] = useState<Session[] | null | 'failed'>(null)
  const [round, setRound] = useState(0)
  const [busy, setBusy] = useState<string | null>(null) // id being signed out
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [asking, setAsking] = useState(false)
  const [allBusy, setAllBusy] = useState(false)
  const [allError, setAllError] = useState<string | null>(null)

  useEffect(() => {
    let live = true
    void api.GET('/api/auth/sessions')
      .then(({ data, response }) => {
        if (live) setSessions(response.ok && Array.isArray(data?.data) && data.data.every((s) => s.id) ? data.data : 'failed')
      })
      .catch(() => { if (live) setSessions('failed') })
    return () => { live = false }
  }, [round])

  const revoke = async (s: Session) => {
    setError(null); setNotice(null); setBusy(s.id!)
    try {
      const { response } = await api.DELETE('/api/auth/sessions/{id}', { params: { path: { id: s.id! } } })
      // 404: that device already signed out or expired — the outcome the user asked for
      if (response.ok || response.status === 404) {
        setSessions((list) => (Array.isArray(list) ? list.filter((x) => x.id !== s.id) : list))
        setNotice(t('sessions.revoked', { device: deviceLabel(s.deviceName) }))
      } else {
        setError(t('sessions.revokeFailed'))
      }
    } catch {
      setError(t('sessions.revokeFailed'))
    } finally {
      setBusy(null)
    }
  }

  const revokeAll = async () => {
    setAllBusy(true); setAllError(null)
    try {
      const { response } = await api.DELETE('/api/auth/sessions')
      if (!response.ok) { setAllError(t('sessions.allFailed')); setAllBusy(false); return }
    } catch {
      setAllError(t('sessions.allFailed')); setAllBusy(false); return
    }
    // every session is revoked already; logout only clears this browser's cookie, so a failure there changes nothing
    if (!(await signOut())) { signedOut(); broadcastSignOut() }
  }

  return (
    <main className="page">
      <h1>{t('sessions.title')}</h1>
      <p className="muted">{t('sessions.intro')}</p>
      {notice && <Alert tone="success" title={notice} />}
      {error && <Alert tone="danger" title={error} />}
      {sessions === null ? (
        <div aria-busy="true"><Skeleton height="12rem" /></div>
      ) : sessions === 'failed' ? (
        <ErrorState title={t('sessions.loadFailed')}
          action={<Button variant="secondary" onClick={() => { setSessions(null); setRound((r) => r + 1) }}>{t('error.retry')}</Button>} />
      ) : (
        <List label={t('sessions.title')}>
          {sessions.map((s) => (
            <ListRow key={s.id}
              title={<>{deviceLabel(s.deviceName)}{s.current && <> · <strong>{t('sessions.thisDevice')}</strong></>}</>}
              subtitle={t('sessions.lastActive', { when: formatWhen(s.lastUsedAt ?? s.createdAt) })}
              trailing={s.current ? undefined : (
                <Button variant="secondary" size="sm" loading={busy === s.id} disabled={busy !== null && busy !== s.id}
                  aria-label={t('sessions.revokeOne', { device: deviceLabel(s.deviceName) })} onClick={() => void revoke(s)}>
                  {t('sessions.revoke')}
                </Button>
              )} />
          ))}
        </List>
      )}
      <Button variant="danger" onClick={() => { setAllError(null); setAsking(true) }}>{t('sessions.all')}</Button>
      <Dialog open={asking} onClose={() => setAsking(false)} title={t('sessions.allTitle')} dismissable={!allBusy}
        actions={<>
          <Button variant="secondary" disabled={allBusy} onClick={() => setAsking(false)}>{t('sessions.keep')}</Button>
          <Button variant="danger" loading={allBusy} onClick={() => void revokeAll()}>{t('sessions.allConfirm')}</Button>
        </>}>
        {allError && <Alert tone="danger" title={allError} />}
        <p>{t('sessions.allBody')}</p>
      </Dialog>
      <p><Link to="/settings/profile">{t('changePin.back')}</Link></p>
    </main>
  )
}
