import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { t, type MessageKey } from '../i18n'
import { Button, CardTile, EmptyState, ErrorState, List, ListRow, Money, Skeleton, type Currency } from '../ui'

type Card = components['schemas']['CardResponse']
type Item = components['schemas']['ActivityItem']

const PAGE_SIZE = 20
const kindLabel: Record<string, MessageKey> = {
  TRANSFER: 'home.kind.TRANSFER', PAYMENT: 'home.kind.PAYMENT', CASHBACK: 'home.kind.CASHBACK',
  TOP_UP: 'home.kind.TOP_UP', OPENING_BALANCE: 'home.kind.OPENING_BALANCE',
}
const when = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

/** Cards (null while loading, 'failed' on error) and the first activity page. */
function useHome() {
  const [cards, setCards] = useState<Card[] | null | 'failed'>(null)
  const [items, setItems] = useState<Item[] | null | 'failed'>(null)
  const [next, setNext] = useState<number | null>(null)
  const [moreBusy, setMoreBusy] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false)
  const [round, setRound] = useState(0)
  const busy = useRef(false)
  const roundRef = useRef(0) // a "show more" answer from before a retry is dropped

  const page = useCallback(async (n: number) => {
    const { data, response } = await api.GET('/api/cards/me/activity', { params: { query: { page: n, size: PAGE_SIZE } } })
    const p = data?.data
    // a row without amount or currency would render as zero or crash Intl: treat the page as failed
    if (!response.ok || !p || !Array.isArray(p.items) || !p.items.every((a) => a.amount && a.currency)) throw new Error('activity')
    return { items: p.items, hasNext: p.hasNext === true }
  }, [])

  useEffect(() => {
    let live = true
    void api.GET('/api/cards/me')
      .then(({ data, response }) => { if (live) setCards(response.ok && Array.isArray(data?.data) && data.data.every((c) => c.amount && c.currency) ? data.data : 'failed') })
      .catch(() => { if (live) setCards('failed') })
    page(0)
      .then((p) => { if (live) { setItems(p.items); setNext(p.hasNext ? 1 : null) } })
      .catch(() => { if (live) setItems('failed') })
    return () => { live = false }
  }, [round, page])

  const loadMore = async () => {
    if (next === null || busy.current) return
    const started = roundRef.current
    busy.current = true; setMoreBusy(true); setMoreFailed(false)
    try {
      const p = await page(next)
      if (roundRef.current !== started) return
      // offset paging: an entry booked since page 0 shifts rows, so page `next` can repeat one already shown
      setItems((cur) => Array.isArray(cur) ? [...cur, ...p.items.filter((a) => !cur.some((c) => c.id === a.id))] : cur)
      setNext(p.hasNext ? next + 1 : null)
    } catch {
      if (roundRef.current === started) setMoreFailed(true)
    } finally {
      busy.current = false; setMoreBusy(false)
    }
  }
  const retry = () => { roundRef.current++; setCards(null); setItems(null); setNext(null); setMoreFailed(false); setRound((r) => r + 1) }
  return { cards, items, hasMore: next !== null, moreBusy, moreFailed, loadMore, retry }
}

export default function Home() {
  const { cards, items, hasMore, moreBusy, moreFailed, loadMore, retry } = useHome()
  const failed = cards === 'failed' || items === 'failed'
  return (
    <main className="page">
      {/* shortcut: no name in the greeting — /api/auth/me "username" is the email; add it with T-113 (own profile) */}
      <h1>{t('home.title')}</h1>
      {Array.isArray(cards) && cards.length > 0 && (
        <div className="home-actions">
          <Link className="btn btn-primary" to="/transfer">{t('home.send')}</Link>
          <Link className="btn btn-secondary" to="/pay">{t('home.pay')}</Link>
        </div>
      )}

      {failed ? (
        <ErrorState title={t('home.failed')} action={<Button variant="secondary" onClick={retry}>{t('error.retry')}</Button>} />
      ) : (
        <>
          {cards === null ? (
            <div aria-busy="true" aria-label={t('home.cards')}><Skeleton height="10rem" radius="var(--radius-lg)" /></div>
          ) : cards.length === 0 ? (
            <EmptyState title={t('home.noCards')} body={t('home.noCardsBody')} />
          ) : (
            <div className="home-cards" role="group" aria-label={t('home.cards')}>
              {cards.map((c) => (
                <CardTile key={c.id} maskedNumber={c.maskedNumber ?? ''} holder={c.cardHolderName ?? ''} expires={c.expirationDate}
                  network={c.cardNetwork ?? ''} currency={c.currency as Currency} balance={c.amount!} />
              ))}
            </div>
          )}

          <section className="home-section" aria-labelledby="recent" aria-busy={items === null}>
            <h2 id="recent">{t('home.recent')}</h2>
            {items === null ? (
              <Skeleton height="3rem" />
            ) : items.length === 0 ? (
              <EmptyState title={t('home.noActivity')} />
            ) : (
              <List>
                {items.map((a) => (
                  <ListRow key={a.id} title={t(kindLabel[a.kind ?? ''] ?? 'home.kind.OTHER')}
                    subtitle={<>
                      <span aria-hidden="true">•••• {a.last4}</span>
                      <span className="sr-only">{t('home.cardEnding', { last4: a.last4 ?? '' })}</span>
                      {a.createdAt && ` · ${when.format(new Date(a.createdAt))}`}
                    </>}
                    trailing={<Money amount={a.amount!} currency={a.currency as Currency} signed />} />
                ))}
              </List>
            )}
            {moreFailed && <p className="muted" role="alert">{t('home.moreFailed')}</p>}
            {hasMore && <Button variant="ghost" size="sm" loading={moreBusy} onClick={() => void loadMore()}>{t('home.more')}</Button>}
          </section>
        </>
      )}
    </main>
  )
}
