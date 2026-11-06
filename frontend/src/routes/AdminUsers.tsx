import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { Alert, Button, Dialog, EmptyState, ErrorState, List, ListRow, Skeleton, TextField } from '../ui'
import { AdminNav } from './AdminNav'
import { problemCode } from './money'

type U = components['schemas']['AdminUserItem']

/** Admin: find users by email or username, lock (signs them out everywhere) or unlock them. */
export default function AdminUsers() {
  const [query, setQuery] = useState('')
  const [submitted, setSubmitted] = useState('')
  const [items, setItems] = useState<U[] | null | 'failed'>(null)
  const [round, setRound] = useState(0)
  const [target, setTarget] = useState<U | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let live = true
    void api.GET('/api/v1/admin/users', { params: { query: { query: submitted, size: 50 } } })
      .then(({ data, response }) => { if (live) setItems(response.ok && Array.isArray(data?.data?.items) ? data.data.items : 'failed') })
      .catch(() => { if (live) setItems('failed') })
    return () => { live = false }
  }, [submitted, round])

  const toggle = async () => {
    if (!target?.id) return
    const lock = target.status !== 'LOCKED'
    setBusy(true); setError(null)
    try {
      const path = { params: { path: { id: target.id } } }
      const { data, error: problem, response } = lock
        ? await api.POST('/api/v1/admin/users/{id}/lock', path)
        : await api.POST('/api/v1/admin/users/{id}/unlock', path)
      if (response.ok && data?.data) {
        const updated = data.data
        setItems((list) => (Array.isArray(list) ? list.map((u) => (u.id === updated.id ? updated : u)) : list))
        setNotice(t(lock ? 'admin.users.locked' : 'admin.users.unlocked', { name: updated.username ?? '' }))
        setTarget(null)
      } else {
        const code = problemCode(problem)
        setError(t(code === 'CANNOT_LOCK_SELF' ? 'admin.users.self' : code === 'ADMIN_TARGET' ? 'admin.users.admin' : 'admin.decideFailed'))
      }
    } catch {
      setError(t('admin.decideFailed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="page">
      <h1>{t('admin.nav.users')}</h1>
      <AdminNav />
      <form className="cards-issue auth-form" role="search" onSubmit={(e) => { e.preventDefault(); setItems(null); setSubmitted(query.trim()); setRound((r) => r + 1) }}>
        <TextField label={t('admin.users.search')} type="search" value={query} onChange={(e) => setQuery(e.target.value)} maxLength={100} />
        <Button type="submit">{t('admin.users.find')}</Button>
      </form>
      {notice && <div ref={(el) => el?.focus()} tabIndex={-1}><Alert tone="success" title={notice} /></div>}
      {items === null ? <div aria-busy="true"><Skeleton height="12rem" /></div>
        : items === 'failed' ? <ErrorState title={t('admin.users.failed')}
            action={<Button variant="secondary" onClick={() => { setItems(null); setRound((r) => r + 1) }}>{t('error.retry')}</Button>} />
        : items.length === 0 ? <EmptyState title={t('admin.users.none')} />
        : (
          <List label={t('admin.nav.users')}>
            {items.map((u) => (
              <ListRow key={u.id}
                title={<>{u.fullName || u.username}{u.status === 'LOCKED' && <> · <strong>{t('admin.users.lockedBadge')}</strong></>}</>}
                subtitle={`@${u.username ?? ''} · ${u.email ?? ''}${u.verified ? ` · ${t('admin.users.verified')}` : ''}${u.roles?.includes('ADMIN_ROLE') ? ` · ${t('admin.users.adminBadge')}` : ''}`}
                trailing={u.roles?.includes('ADMIN_ROLE') ? undefined : (
                  <Button size="sm" variant={u.status === 'LOCKED' ? 'secondary' : 'danger'}
                    aria-label={t(u.status === 'LOCKED' ? 'admin.users.unlockOne' : 'admin.users.lockOne', { name: u.username ?? '' })}
                    onClick={() => { setError(null); setNotice(null); setTarget(u) }}>
                    {t(u.status === 'LOCKED' ? 'admin.users.unlock' : 'admin.users.lock')}
                  </Button>
                )} />
            ))}
          </List>
        )}
      <Dialog open={target !== null} onClose={() => setTarget(null)} dismissable={!busy}
        title={t(target?.status === 'LOCKED' ? 'admin.users.unlockTitle' : 'admin.users.lockTitle', { name: target?.username ?? '' })}
        actions={<>
          <Button variant="secondary" disabled={busy} onClick={() => setTarget(null)}>{t('sessions.keep')}</Button>
          <Button variant={target?.status === 'LOCKED' ? 'primary' : 'danger'} loading={busy} onClick={() => void toggle()}>
            {t(target?.status === 'LOCKED' ? 'admin.users.unlock' : 'admin.users.lock')}
          </Button>
        </>}>
        {error && <Alert tone="danger" title={error} />}
        <p>{t(target?.status === 'LOCKED' ? 'admin.users.unlockBody' : 'admin.users.lockBody')}</p>
      </Dialog>
    </main>
  )
}
