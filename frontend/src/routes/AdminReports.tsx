import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { Alert, Button, EmptyState, ErrorState, Skeleton } from '../ui'
import { AdminNav } from './AdminNav'
import { formatWhen } from './kyc'

type R = components['schemas']['AdminReportItem']
type Status = NonNullable<R['status']>
const STATUSES: Status[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'REJECTED']

/** Admin: problem reports by status, newest first; each can be moved to another status. */
export default function AdminReports() {
  const [status, setStatus] = useState<Status>('OPEN')
  const [items, setItems] = useState<R[] | null | 'failed'>(null)
  const [round, setRound] = useState(0)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<number | null>(null)
  const generation = useRef(0)

  useEffect(() => {
    let live = true
    void api.GET('/api/admin/reports', { params: { query: { status, size: 50 } } })
      .then(({ data, response }) => { if (live) setItems(response.ok && Array.isArray(data?.data?.items) ? data.data.items : 'failed') })
      .catch(() => { if (live) setItems('failed') })
    return () => { live = false }
  }, [status, round])

  const move = async (r: R, next: Status) => {
    if (!r.id) return
    const started = generation.current
    setBusy(r.id); setError(null)
    try {
      const { response } = await api.PATCH('/api/admin/reports/{id}', { params: { path: { id: r.id } }, body: { status: next } })
      if (generation.current !== started) return
      if (response.ok) setItems((list) => (Array.isArray(list) ? list.filter((x) => x.id !== r.id) : list)) // it left this tab
      else setError(t('admin.decideFailed'))
    } catch {
      setError(t('admin.decideFailed'))
    } finally {
      setBusy(null)
    }
  }

  return (
    <main className="page">
      <h1>{t('admin.nav.reports')}</h1>
      <AdminNav />
      <div className="tabs" role="group" aria-label={t('admin.kycFilter')}>
        {STATUSES.map((s) => (
          <Button key={s} size="sm" variant={s === status ? 'primary' : 'secondary'} aria-pressed={s === status}
            onClick={() => { if (s !== status) { generation.current++; setItems(null); setStatus(s) } }}>
            {t(`admin.report.${s}`)}
          </Button>
        ))}
      </div>
      {error && <Alert tone="danger" title={error} />}
      {items === null ? <div aria-busy="true"><Skeleton height="12rem" /></div>
        : items === 'failed' ? <ErrorState title={t('admin.reports.failed')}
            action={<Button variant="secondary" onClick={() => { setItems(null); setRound((x) => x + 1) }}>{t('error.retry')}</Button>} />
        : items.length === 0 ? <EmptyState title={t('admin.reports.none')} />
        : (
          <ul className="report-list">
            {items.map((r) => (
              <li key={r.id} className="home-section">
                <h2>{t(`report.cat.${r.category ?? 'TRANSACTION_FAILED'}`)}</h2>
                <p className="muted">#{r.id} · @{r.username ?? '—'} · {r.email} · {formatWhen(r.createdAt)}</p>
                <p className="legal-body">{r.message}</p>
                {r.referenceId && <p className="muted">{t('admin.reports.reference')}: {r.referenceId}</p>}
                <div className="field">
                  <label htmlFor={`status-${r.id}`} className="field-label">{t('admin.reports.moveTo')}</label>
                  <div className="field-control">
                    <select id={`status-${r.id}`} value={r.status} disabled={busy === r.id}
                      onChange={(e) => void move(r, e.target.value as Status)}>
                      {STATUSES.map((s) => <option key={s} value={s}>{t(`admin.report.${s}`)}</option>)}
                    </select>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
    </main>
  )
}
