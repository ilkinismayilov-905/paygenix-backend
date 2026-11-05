import { useState } from 'react'
import { api } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, CardTile, Dialog, EmptyState, ErrorState, Skeleton, type Currency } from '../ui'
import { useOwnCards } from './money'

const CURRENCIES: Currency[] = ['AZN', 'USD', 'EUR']

/** "4000001984948778" → "4000 0019 8494 8778" */
const groups = (pan: string) => pan.replace(/(\d{4})(?=\d)/g, '$1 ')

export default function Cards() {
  const { cards, reload, retry } = useOwnCards()
  const [currency, setCurrency] = useState<Currency>('AZN')
  const [busy, setBusy] = useState(false)
  const [failed, setFailed] = useState(false)
  const [issued, setIssued] = useState<string | null>(null) // the full number, shown once

  const issue = async () => {
    setBusy(true); setFailed(false)
    try {
      const { data, response } = await api.POST('/api/cards', { body: { currency } })
      const pan = data?.data?.cardNumber
      if (response.ok && pan) setIssued(pan)
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
      <h1>{t('cards.title')}</h1>
      <section className="home-section cards-issue" aria-label={t('cards.issue')}>
        {failed && <Alert tone="danger" title={t('cards.issueFailed')} />}
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

      <Dialog open={issued !== null} onClose={() => setIssued(null)} title={t('cards.issued')}
        actions={<Button onClick={() => setIssued(null)}>{t('cards.done')}</Button>}>
        <p className="cards-pan">{issued && groups(issued)}</p>
        <p className="muted">{t('cards.issuedBody')}</p>
      </Dialog>
    </main>
  )
}
