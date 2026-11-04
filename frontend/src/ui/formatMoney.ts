export type Currency = 'AZN' | 'USD' | 'EUR'

const formatters = new Map<string, Intl.NumberFormat>()
const DECIMAL = /^-?\d+(\.\d+)?$/

/**
 * Formats a decimal string for display. The amount stays a string (ADR 0001): Intl formats
 * string decimals exactly (Intl.NumberFormat v3: Chrome 106+, Safari 15.4+, Firefox 116+),
 * so no value ever passes through a JS number.
 */
export function formatMoney(amount: string, currency: Currency, signed = false): string {
  // A blank or pre-formatted value would otherwise show as 0.00 or NaN.
  if (!DECIMAL.test(amount)) throw new Error(`Not a decimal amount: "${amount}"`)
  const key = `${currency}:${signed}`
  let f = formatters.get(key)
  if (!f) {
    f = new Intl.NumberFormat('en', { style: 'currency', currency, currencyDisplay: 'narrowSymbol', signDisplay: signed ? 'exceptZero' : 'auto' })
    formatters.set(key, f)
  }
  return f.format(amount as Intl.StringNumericLiteral)
}
