import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import type { components } from '../api/schema'

// Shared by the dashboard (all cards) and the card detail (one card).

type Item = components['schemas']['ActivityItem']

const PAGE_SIZE = 20

/** Activity pages, newest first: null while loading, 'failed' on error. `retry` reloads from page 0. */
export function useActivity(cardId?: number) {
  const [items, setItems] = useState<Item[] | null | 'failed'>(null)
  const [next, setNext] = useState<number | null>(null)
  const [moreBusy, setMoreBusy] = useState(false)
  const [moreFailed, setMoreFailed] = useState(false)
  const [round, setRound] = useState(0)
  const busy = useRef(false)
  const roundRef = useRef(0) // a "show more" answer from before a retry is dropped

  const page = useCallback(async (n: number) => {
    const { data, response } = await api.GET('/api/cards/me/activity', { params: { query: { page: n, size: PAGE_SIZE, cardId } } })
    const p = data?.data
    // a row without amount or currency would render as zero or crash Intl: treat the page as failed
    if (!response.ok || !p || !Array.isArray(p.items) || !p.items.every((a) => a.amount && a.currency)) throw new Error('activity')
    return { items: p.items, hasNext: p.hasNext === true }
  }, [cardId])

  useEffect(() => {
    let live = true
    page(0)
      .then((p) => { if (live) { setItems(p.items); setNext(p.hasNext ? 1 : null) } })
      .catch(() => { if (live) setItems('failed') })
    return () => { live = false }
  }, [round, page])

  const loadMore = async () => {
    if (next === null || busy.current) return
    const started = roundRef.current
    busy.current = true; setMoreBusy(true); setMoreFailed(false)
    try {
      const p = await page(next)
      if (roundRef.current !== started) return
      // offset paging: an entry booked since page 0 shifts rows, so page `next` can repeat one already shown
      setItems((cur) => Array.isArray(cur) ? [...cur, ...p.items.filter((a) => !cur.some((c) => c.id === a.id))] : cur)
      setNext(p.hasNext ? next + 1 : null)
    } catch {
      if (roundRef.current === started) setMoreFailed(true)
    } finally {
      busy.current = false; setMoreBusy(false)
    }
  }
  const retry = () => { roundRef.current++; setItems(null); setNext(null); setMoreFailed(false); setRound((r) => r + 1) }
  return { items, hasMore: next !== null, moreBusy, moreFailed, loadMore, retry }
}

