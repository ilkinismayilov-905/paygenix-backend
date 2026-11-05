import { t, type MessageKey } from '../i18n'
import { Button, EmptyState, List, ListRow, Money, Skeleton, type Currency } from '../ui'
import type { useActivity } from './activity'

const kindLabel: Record<string, MessageKey> = {
  TRANSFER: 'home.kind.TRANSFER', PAYMENT: 'home.kind.PAYMENT', CASHBACK: 'home.kind.CASHBACK',
  TOP_UP: 'home.kind.TOP_UP', OPENING_BALANCE: 'home.kind.OPENING_BALANCE',
}
const when = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })

/** The "Recent activity" section; the caller shows the error state when items is 'failed'. */
export function ActivitySection({ activity }: { activity: ReturnType<typeof useActivity> }) {
  const { items, hasMore, moreBusy, moreFailed, loadMore } = activity
  return (
    <section className="home-section" aria-labelledby="recent" aria-busy={items === null}>
      <h2 id="recent">{t('home.recent')}</h2>
      {items === null || items === 'failed' ? (
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
  )
}
