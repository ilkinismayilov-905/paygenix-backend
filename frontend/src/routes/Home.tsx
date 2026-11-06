import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { t } from '../i18n'
import { Button, CardTile, EmptyState, ErrorState, Skeleton, type Currency } from '../ui'
import { useActivity } from './activity'
import { ActivitySection } from './ActivitySection'
import { useOwnCards } from './money'
import { StoriesRow } from './Stories'

/** Cards and the first activity page; one retry reloads both. */
function useHome() {
  const { cards, retry: retryCards } = useOwnCards()
  const activity = useActivity()
  return { cards, activity, retry: () => { retryCards(); activity.retry() } }
}

/** First name for the greeting; null until loaded, or when there is none (the greeting is optional, errors are ignored). */
function useFirstName() {
  const [name, setName] = useState<string | null>(null)
  useEffect(() => {
    let live = true
    void api.GET('/api/profile/me')
      .then(({ data }) => { if (live) setName(data?.data?.fullName?.trim().split(/\s+/)[0] || null) })
      .catch(() => {})
    return () => { live = false }
  }, [])
  return name
}

export default function Home() {
  const name = useFirstName()
  const { cards, activity, retry } = useHome()
  const failed = cards === 'failed' || activity.items === 'failed'
  return (
    <main className="page">
      <h1>{name ? t('home.hello', { name }) : t('home.title')}</h1>
      <StoriesRow />
      {Array.isArray(cards) && cards.length > 0 && (
        <div className="home-actions">
          <Link className="btn btn-primary" to="/transfer">{t('home.send')}</Link>
          <Link className="btn btn-secondary" to="/pay">{t('home.pay')}</Link>
          <Link className="btn btn-secondary" to="/top-up">{t('home.topUp')}</Link>
        </div>
      )}

      {failed ? (
        <ErrorState title={t('home.failed')} action={<Button variant="secondary" onClick={retry}>{t('error.retry')}</Button>} />
      ) : (
        <>
          {cards === null ? (
            <div aria-busy="true" aria-label={t('home.cards')}><Skeleton height="10rem" radius="var(--radius-lg)" /></div>
          ) : cards.length === 0 ? (
            <EmptyState title={t('home.noCards')} body={t('home.noCardsBody')}
              action={<Link className="btn btn-primary" to="/cards">{t('cards.issue')}</Link>} />
          ) : (
            <div className="home-cards" role="group" aria-label={t('home.cards')}>
              {cards.map((c) => (
                <CardTile key={c.id} to={`/cards/${c.id}`} maskedNumber={c.maskedNumber ?? ''} holder={c.cardHolderName ?? ''} expires={c.expirationDate}
                  network={c.cardNetwork ?? ''} currency={c.currency as Currency} balance={c.amount!} />
              ))}
            </div>
          )}

          <ActivitySection activity={activity} />
        </>
      )}
    </main>
  )
}
