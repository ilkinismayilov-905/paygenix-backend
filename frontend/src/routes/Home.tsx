import { useCallback, useEffect, useState } from 'react'
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

  const page = useCallback(async (n: number) => {
    const { data, response } = await api.GET('/api/cards/me/activity', { params: { query: { page: n, size: PAGE_SIZE } } })
    const p = data?.data
    if (!response.ok || !p || !Array.isArray(p.items)) throw new Error('activity')
    return { items: p.items, hasNext: p.hasNext === true }
  }, [])

  useEffect(() => {
    let live = true
    void api.GET('/api/cards/me')
      .then(({ data, response }) => { if (live) setCards(response.ok && Array.isArray(data?.data) ? data.data : 'failed') })
      .catch(() => { if (live) setCards('failed') })
    page(0)
      .then((p) => { if (live) { setItems(p.items); setNext(p.hasNext ? 1 : null) } })
      .catch(() => { if (live) setItems('failed') })
    return () => { live = false }
  }, [round, page])

  const loadMore = async () => {
    if (next === null || !Array.isArray(items)) return
    setMoreBusy(true); setMoreFailed(false)
    try {
      const p = await page(next)
      setItems([...items, ...p.items])
      setNext(p.hasNext ? next + 1 : null)
    } catch {
      setMoreFailed(true)
    } finally {
      setMoreBusy(false)
    }
  }
  const retry = () => { setCards(null); setItems(null); setRound((r) => r + 1) }
  return { cards, items, hasMore: next !== null, moreBusy, moreFailed, loadMore, retry }
}

export default function Home() {
  const { cards, items, hasMore, moreBusy, moreFailed, loadMore, retry } = useHome()
  const failed = cards === 'failed' || items === 'failed'
  return (
    <main className="page">
      {/* shortcut: no name in the greeting — /api/auth/me "username" is the email; add it with T-113 (own profile) */}
      <h1>{t('home.title')}</h1>

      {failed ? (
        <ErrorState title={t('home.failed')} action={<Button variant="secondary" onClick={retry}>{t('error.retry')}</Button>} />
      ) : (
        <>
          {cards === null ? (
            <Skeleton height="10rem" radius="var(--radius-lg)" />
          ) : cards.length === 0 ? (
            <EmptyState title={t('home.noCards')} body={t('home.noCardsBody')} />
          ) : (
            <div className="home-cards" role="group" aria-label={t('home.cards')}>
              {cards.map((c) => (
                <CardTile key={c.id} maskedNumber={c.maskedNumber ?? ''} holder={c.cardHolderName ?? ''} expires={c.expirationDate}
                  network={c.cardNetwork ?? ''} currency={c.currency as Currency} balance={c.amount ?? '0.00'} />
              ))}
            </div>
          )}

          <section className="home-section" aria-labelledby="recent">
            <h2 id="recent">{t('home.recent')}</h2>
            {items === null ? (
              <Skeleton height="3rem" />
            ) : items.length === 0 ? (
              <EmptyState title={t('home.noActivity')} />
            ) : (
              <List>
                {items.map((a) => (
                  <ListRow key={a.id} title={t(kindLabel[a.kind ?? ''] ?? 'home.kind.OTHER')}
                    subtitle={`•••• ${a.last4} · ${a.createdAt ? when.format(new Date(a.createdAt)) : ''}`}
                    trailing={<Money amount={a.amount ?? '0'} currency={a.currency as Currency} signed />} />
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
