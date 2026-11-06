import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { api } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, EmptyState, ErrorState, List, ListRow, Skeleton } from '../ui'

import { formatWhen, type Review } from './kyc'

type Status = NonNullable<Review['status']>

const STATUSES: Status[] = ['PENDING', 'APPROVED', 'REJECTED']
const PAGE_SIZE = 20

/** Admin: identity documents by status (GET /api/admin/kyc), pending ones oldest first. */
export default function AdminKyc() {
  const notice = (useLocation().state as { reviewed?: string } | null)?.reviewed
  const [status, setStatus] = useState<Status>('PENDING')
  const [items, setItems] = useState<Review[] | null | 'failed'>(null)
  const [next, setNext] = useState<number | null>(null)
  const [more, setMore] = useState<'idle' | 'busy' | 'failed'>('idle')
  const [round, setRound] = useState(0)
  const shown = useRef(status) // a page for another tab that lands late is dropped

  const load = useCallback(async (s: Status, page: number) => {
    const { data, response } = await api.GET('/api/admin/kyc', { params: { query: { status: s, page, size: PAGE_SIZE } } })
    const p = data?.data
    if (!response.ok || !p || !Array.isArray(p.items)) throw new Error('kyc')
    return { items: p.items, next: p.hasNext ? page + 1 : null }
  }, [])

  useEffect(() => {
    let live = true
    load(status, 0)
      .then((p) => { if (live) { setItems(p.items); setNext(p.next) } })
      .catch(() => { if (live) setItems('failed') })
    return () => { live = false }
  }, [status, round, load])

  const loadMore = async () => {
    if (next === null || more === 'busy') return
    const s = status
    setMore('busy')
    try {
      const p = await load(s, next)
      if (shown.current !== s) return
      setItems((list) => (Array.isArray(list) ? [...list, ...p.items.filter((i) => !list.some((x) => x.id === i.id))] : list))
      setNext(p.next)
      setMore('idle')
    } catch {
      if (shown.current === s) setMore('failed')
    }
  }

  const choose = (s: Status) => {
    if (s === status) return
    shown.current = s
    setItems(null); setNext(null); setMore('idle'); setStatus(s)
  }

  return (
    <main className="page">
      <h1>{t('admin.kycTitle')}</h1>
      <p><Link to="/admin/stories">{t('promo.title')}</Link></p>
      {notice && <Alert tone="success" title={notice} />}
      <div className="tabs" role="group" aria-label={t('admin.kycFilter')}>
        {STATUSES.map((s) => (
          <Button key={s} size="sm" variant={s === status ? 'primary' : 'secondary'} aria-pressed={s === status} onClick={() => choose(s)}>
            {t(`admin.status.${s}`)}
          </Button>
        ))}
      </div>
      {items === null ? <div aria-busy="true"><Skeleton height="12rem" /></div>
        : items === 'failed' ? <ErrorState title={t('admin.kycFailed')}
            action={<Button variant="secondary" onClick={() => { setItems(null); setRound((r) => r + 1) }}>{t('error.retry')}</Button>} />
        : items.length === 0 ? <EmptyState title={t(status === 'PENDING' ? 'admin.kycNonePending' : 'admin.kycNone')} />
        : (
          <List label={t(`admin.status.${status}`)}>
            {items.map((k) => (
              <ListRow key={k.id}
                title={<Link to={`/admin/kyc/${k.id}`}>{k.fullName || k.username}</Link>}
                subtitle={`${t(`verify.doc.${k.documentType ?? 'ID_CARD'}`)} · ${t('admin.sent', { when: formatWhen(k.submittedAt) })}`}
                trailing={k.username ? <span className="muted">@{k.username}</span> : undefined} />
            ))}
          </List>
        )}
      {more === 'failed' && <p className="muted" role="alert">{t('home.moreFailed')}</p>}
      {next !== null && Array.isArray(items) && (
        <Button variant="ghost" size="sm" loading={more === 'busy'} onClick={() => void loadMore()}>{t('home.more')}</Button>
      )}
    </main>
  )
}
