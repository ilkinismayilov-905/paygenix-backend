import type { Currency } from './formatMoney'
import { Money } from './Money'

type Props = {
  maskedNumber: string
  holder: string
  expires: string
  network: string
  currency: Currency
  balance: string
  selected?: boolean
  onSelect?: () => void
}

const networkName: Record<string, string> = { VISA: 'Visa', MASTERCARD: 'Mastercard', AMEX: 'Amex', DISCO: 'Discover', DINERS: 'Diners' }

export function CardTile({ maskedNumber, holder, expires, network, currency, balance, selected, onSelect }: Props) {
  const last4 = maskedNumber.replace(/\D/g, '').slice(-4)
  const body = (
    <>
      <span className="cardtile-top">
        <span className="cardtile-currency">{currency}</span>
        <span className="cardtile-network">{networkName[network] ?? network}</span>
      </span>
      <Money amount={balance} currency={currency} size="lg" />
      <span className="cardtile-bottom">
        <span className="cardtile-number" aria-label={`Card ending in ${last4}`}>•••• {last4}</span>
        <span className="cardtile-meta">{holder}</span>
        <span className="cardtile-meta">{expires}</span>
      </span>
    </>
  )
  return onSelect ? (
    <button type="button" className="cardtile" aria-pressed={selected} onClick={onSelect}>{body}</button>
  ) : (
    <div className="cardtile">{body}</div>
  )
}
