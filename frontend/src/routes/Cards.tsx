import { useCallback, useRef, useState } from 'react'
import { useLocation } from 'react-router'
import { api } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, CardTile, Dialog, EmptyState, ErrorState, Skeleton, type Currency } from '../ui'
import { useOwnCards } from './money'

const CURRENCIES: Currency[] = ['AZN', 'USD', 'EUR']

/** "4000001984948778" → "4000 0019 8494 8778" */
const groups = (pan: string) => pan.replace(/(\d{4})(?=\d)/g, '$1 ')

export default function Cards() {
  const { cards, reload, retry } = useOwnCards()
  // After a close the detail page is gone; focus lands on this heading instead of <body>. Stable ref: once per mount of that history entry.
  const closed = (useLocation().state as { closed?: boolean } | null)?.closed === true
  const focusOnMount = useCallback((el: HTMLElement | null) => el?.focus(), [])
  const [currency, setCurrency] = useState<Currency>('AZN')
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  const [issued, setIssued] = useState<string | null>(null) // the full number, shown once
  const [replayed, setReplayed] = useState(false)
  // A retry after a lost answer reuses the key, so the server returns that card instead of issuing a second one.
  // shortcut: only the last currency's key is kept; switching away and back mints a new key (the list reload shows the first card)
  const attempt = useRef<{ currency: Currency; key: string } | null>(null)

  const issue = async () => {
    setBusy(true); setFailed(false); setReplayed(false)
    const key = attempt.current?.currency === currency ? attempt.current.key : crypto.randomUUID()
    attempt.current = { currency, key }
    try {
      const { data, response } = await api.POST('/api/cards',
        { body: { currency }, params: { header: { 'Idempotency-Key': key } } })
      if (response.ok) attempt.current = null // the next click is a new card
      const pan = data?.data?.cardNumber
      if (response.ok && pan) setIssued(pan)
      else if (response.ok) setReplayed(true) // the earlier try had issued it; its number is not kept
      else setFailed(true)
    } catch {
      setFailed(true)
    } finally {
      setBusy(false)
      reload() // also after a lost answer: the card may exist anyway
    }
  }

  return (
    <main className="page">
      <h1 ref={closed ? focusOnMount : undefined} tabIndex={-1}>{t('cards.title')}</h1>
      <section className="home-section cards-issue" aria-label={t('cards.issue')}>
        {failed && <Alert tone="danger" title={t('cards.issueFailed')} />}
        {replayed && <Alert tone="info" title={t('cards.issuedEarlier')} />}
        <div className="field">
          <label htmlFor="currency" className="field-label">{t('cards.currency')}</label>
          <div className="field-control">
            <select id="currency" value={currency} onChange={(e) => setCurrency(e.target.value as Currency)}>
              {CURRENCIES.map((c) => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
        </div>
        <Button loading={busy} onClick={() => void issue()}>{t('cards.issue')}</Button>
      </section>

      {cards === null ? (
        <div aria-busy="true"><Skeleton height="10rem" radius="var(--radius-lg)" /></div>
      ) : cards === 'failed' ? (
        <ErrorState title={t('money.cardsFailed')} action={<Button variant="secondary" onClick={retry}>{t('error.retry')}</Button>} />
      ) : cards.length === 0 ? (
        <EmptyState title={t('home.noCards')} body={t('home.noCardsBody')} />
      ) : (
        <ul className="cards-list" aria-label={t('cards.title')}>
          {cards.map((c) => (
            <li key={c.id}>
              <CardTile to={`/cards/${c.id}`} maskedNumber={c.maskedNumber ?? ''} holder={c.cardHolderName ?? ''} expires={c.expirationDate}
                network={c.cardNetwork ?? ''} currency={c.currency as Currency} balance={c.amount!} />
            </li>
          ))}
        </ul>
      )}

      <Dialog open={issued !== null} onClose={() => setIssued(null)} title={t('cards.issued')} dismissable={false}
        actions={<Button onClick={() => setIssued(null)}>{t('cards.done')}</Button>}>
        <p className="cards-pan">{issued && groups(issued)}</p>
        <p className="muted">{t('cards.issuedBody')}</p>
      </Dialog>
    </main>
  )
}
