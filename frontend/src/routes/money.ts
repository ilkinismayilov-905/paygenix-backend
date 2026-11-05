import { useEffect, useState } from 'react'
import { z } from 'zod'
import { api, type Problem } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { formatMoney, type Currency } from '../ui'

// Shared by the money-moving screens (transfer, pay).

export type Card = components['schemas']['CardResponse']

/** "010,5" and "10.50" are the same money: one spelling, so an idempotency key sees them as one request. */
function canonical(amount: string): string {
  const [int = '', frac = ''] = amount.split('.')
  return `${int.replace(/^0+(?=\d)/, '')}.${frac.padEnd(2, '0')}`
}

/** Mirrors the backend amount rule: 0.01+ with at most 2 decimals; a comma works as the decimal point. */
export const amountSchema = z.string().transform((s) => s.trim().replace(',', '.'))
  .pipe(z.string().regex(/^\d{1,15}(\.\d{1,2})?$/, t('money.amountInvalid')).refine((s) => /[1-9]/.test(s), t('money.amountInvalid')))
  .transform(canonical)

/** The problem+json `code` the UI branches on. */
export const problemCode = (problem: unknown) => (problem as Problem | undefined)?.code

export const pinSchema = z.string().regex(/^\d{6}$/, t('money.pinInvalid'))

/** The caller's cards: null while loading, 'failed' on error. `reload` refetches (balances change after a move). */
export function useOwnCards() {
  const [cards, setCards] = useState<Card[] | null | 'failed'>(null)
  const [round, setRound] = useState(0)
  useEffect(() => {
    let live = true
    void api.GET('/api/cards/me')
      .then(({ data, response }) => {
        if (live) setCards(response.ok && Array.isArray(data?.data) && data.data.every((c) => c.id && c.currency) ? data.data : 'failed')
      })
      .catch(() => { if (live) setCards('failed') })
    return () => { live = false }
  }, [round])
  return { cards, reload: () => setRound((r) => r + 1), retry: () => { setCards(null); setRound((r) => r + 1) } }
}

export const cardLabel = (c: Card) =>
  `•••• ${c.maskedNumber?.slice(-4) ?? ''} · ${c.amount ? formatMoney(c.amount, c.currency as Currency) : ''}`
