import { useEffect, useRef, type ReactNode } from 'react'
import { Link } from 'react-router'
import { t } from '../i18n'
import { Button, EmptyState, ErrorState, Money, Skeleton, type Currency } from '../ui'
import type { Card } from './money'

/** Loading, error and no-card states; renders `children` only when there is at least one card. */
export function CardsGate({ cards, retry, children }: { cards: Card[] | null | 'failed'; retry: () => void; children: (cards: Card[]) => ReactNode }) {
  if (cards === null) return <div aria-busy="true"><Skeleton height="20rem" /></div>
  if (cards === 'failed') {
    return <ErrorState title={t('money.cardsFailed')} action={<Button variant="secondary" onClick={retry}>{t('error.retry')}</Button>} />
  }
  if (cards.length === 0) return <EmptyState title={t('home.noCards')} body={t('home.noCardsBody')} />
  return children(cards)
}

/** Result view. Focus moves to the heading because the form it replaces is gone, so screen readers read the result. */
export function MoneyDone({ title, amount, currency, detail, again, againLabel }:
  { title: string; amount: string; currency: Currency; detail: ReactNode; again: () => void; againLabel: string }) {
  const heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => { heading.current?.focus() }, [])
  return (
    <main className="page">
      <section className="transfer-done" aria-labelledby="money-done">
        <h1 id="money-done" ref={heading} tabIndex={-1}>{title}</h1>
        <Money amount={amount} currency={currency} size="xl" />
        <p className="muted">{detail}</p>
        <div className="transfer-actions">
          <Link className="btn btn-primary" to="/">{t('money.home')}</Link>
          <Button variant="secondary" onClick={again}>{againLabel}</Button>
        </div>
      </section>
    </main>
  )
}
