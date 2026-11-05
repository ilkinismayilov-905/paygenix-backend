import { Link } from 'react-router'
import type { Currency } from './formatMoney'
import { Money } from './Money'

type Props = {
  maskedNumber: string
  holder: string
  expires?: string | null
  network: string
  currency: Currency
  /** Raw decimal string, not a pre-formatted display value. */
  balance: string
  selected?: boolean
  onSelect?: () => void
  /** Makes the tile a link (e.g. to the card detail). */
  to?: string
}

const networkName: Record<string, string> = { VISA: 'Visa', MASTERCARD: 'Mastercard', AMEX: 'Amex', DISCO: 'Discover', DINERS: 'Diners' }

export function CardTile({ maskedNumber, holder, expires, network, currency, balance, selected, onSelect, to }: Props) {
  const last4 = maskedNumber.replace(/\D/g, '').slice(-4)
  const body = (
    <>
      <span className="cardtile-top">
        <span className="cardtile-currency">{currency}</span>
        <span className="cardtile-network">{networkName[network] ?? network}</span>
      </span>
      <Money amount={balance} currency={currency} size="lg" />
      <span className="cardtile-bottom">
        <span className="cardtile-number">
          <span aria-hidden="true">•••• {last4}</span>
          <span className="sr-only">Card ending in {last4}</span>
        </span>
        <span className="cardtile-meta">{holder}</span>
        {expires && <span className="cardtile-meta">{expires}</span>}
      </span>
    </>
  )
  if (to) return <Link className="cardtile" to={to}>{body}</Link>
  return onSelect ? (
    <button type="button" className="cardtile" aria-pressed={!!selected} onClick={onSelect}>{body}</button>
  ) : (
    <div className="cardtile">{body}</div>
  )
}
