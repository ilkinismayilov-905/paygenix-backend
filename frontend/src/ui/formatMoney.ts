export type Currency = 'AZN' | 'USD' | 'EUR'

const formatters = new Map<string, Intl.NumberFormat>()

/**
 * Formats a decimal string for display. The amount stays a string (ADR 0001): Intl formats
 * string decimals exactly, so no value ever passes through a JS number.
 */
export function formatMoney(amount: string, currency: Currency, signed = false): string {
  const key = `${currency}:${signed}`
  let f = formatters.get(key)
  if (!f) {
    f = new Intl.NumberFormat('en', { style: 'currency', currency, currencyDisplay: 'narrowSymbol', signDisplay: signed ? 'exceptZero' : 'auto' })
    formatters.set(key, f)
  }
  return f.format(amount as Intl.StringNumericLiteral)
}
