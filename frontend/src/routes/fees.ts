import { useEffect, useState } from 'react'
import { api } from '../api/client'

export type Fees = { commissionPercent: string; cashbackPercent: string }

let cached: Promise<Fees | null> | null = null

/** GET /api/fees, once per page load; null while loading or when it failed (screens then show the rate-less hint). */
export function useFees(): Fees | null {
  const [fees, setFees] = useState<Fees | null>(null)
  useEffect(() => {
    let live = true
    cached ??= api.GET('/api/fees')
      .then(({ data, response }) => {
        const f = data?.data
        return response.ok && f?.commissionPercent && f.cashbackPercent ? { commissionPercent: f.commissionPercent, cashbackPercent: f.cashbackPercent } : null
      })
      .catch(() => null)
      .then((f) => { if (!f) cached = null; return f }) // a failure is retried by the next screen
    void cached.then((f) => { if (live) setFees(f) })
    return () => { live = false }
  }, [])
  return fees
}

const toCents = (amount: string) => {
  const [int = '0', frac = ''] = amount.split('.')
  return BigInt(int) * 100n + BigInt((frac + '00').slice(0, 2))
}
const fromCents = (c: bigint) => `${c / 100n}.${(c % 100n).toString().padStart(2, '0')}`

/**
 * percent% of a canonical amount ("10.50"), rounded half-even to cents like the server (FeeProperties). Exact: BigInt, never a
 * float. Amounts here are never negative.
 */
export function percentOf(amount: string, percent: string): string {
  const [pInt = '0', pFrac = ''] = percent.split('.')
  const scale = 10n ** BigInt(pFrac.length)
  const n = toCents(amount) * BigInt(pInt + pFrac)
  const den = 100n * scale
  let q = n / den
  const r2 = (n % den) * 2n
  if (r2 > den || (r2 === den && q % 2n === 1n)) q += 1n
  return fromCents(q)
}

export const addAmounts = (a: string, b: string) => fromCents(toCents(a) + toCents(b))
