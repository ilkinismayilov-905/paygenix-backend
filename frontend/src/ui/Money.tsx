import { formatMoney, type Currency } from './formatMoney'

type Props = {
  amount: string
  currency: Currency
  size?: 'sm' | 'md' | 'lg' | 'xl'
  /** Show + for incoming and colour by direction. */
  signed?: boolean
}

export function Money({ amount, currency, size = 'md', signed }: Props) {
  const negative = amount.trim().startsWith('-')
  const tone = signed ? (negative ? 'money-out' : 'money-in') : ''
  return <span className={['money', `money-${size}`, tone].filter(Boolean).join(' ')}>{formatMoney(amount, currency, signed)}</span>
}
