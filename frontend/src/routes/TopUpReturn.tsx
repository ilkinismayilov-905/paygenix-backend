import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { Alert, Money, Skeleton, type Currency } from '../ui'

type TopUp = components['schemas']['TopUpResponse']

const POLL_MS = 2000
const POLL_TRIES = 15 // about 30 s: Stripe's webhook usually lands within seconds

/**
 * Where Stripe sends the browser back (?session=...). The card is credited by Stripe's webhook, not by this page,
 * so it asks the server until the top-up is no longer PENDING.
 */
export default function TopUpReturn() {
  const [params] = useSearchParams()
  // keyed: another session in the URL starts from scratch
  return <Result key={params.get('session') ?? ''} session={params.get('session') ?? ''} cancelled={params.has('cancelled')} />
}

function Result({ session, cancelled }: { session: string; cancelled: boolean }) {
  const [topUp, setTopUp] = useState<TopUp | null | 'missing'>(null)
  const [gaveUp, setGaveUp] = useState(false)

  useEffect(() => {
    if (!session || cancelled) return // a cancelled checkout charged nothing: no need to ask
    const run = { stopped: false }
    void (async () => {
      for (let i = 0; i < POLL_TRIES; i++) {
        if (i > 0) await new Promise((r) => setTimeout(r, POLL_MS))
        if (run.stopped) return
        try {
          const { data, response } = await api.GET('/api/v1/topups/session/{sessionId}', { params: { path: { sessionId: session } } })
          if (run.stopped) return
          if (response.status === 404) return setTopUp('missing')
          const found = data?.data
          if (response.ok && found?.status) {
            setTopUp(found)
            if (found.status !== 'PENDING') return
          }
        } catch {
          // offline for a moment: the next round asks again
        }
      }
      if (!run.stopped) setGaveUp(true)
    })()
    return () => { run.stopped = true }
  }, [session, cancelled])

  const pending = topUp === null || (topUp !== 'missing' && topUp.status === 'PENDING')
  const stillPolling = pending && !gaveUp && !cancelled

  let body
  if (cancelled) body = <Alert title={t('topUp.cancelled')} />
  else if (!session || topUp === 'missing') body = <Alert tone="danger" title={t('topUp.unknown')} />
  else if (topUp && topUp.status === 'SUCCEEDED') body = (
    <section className="transfer-done" aria-labelledby="topup-done">
      <h2 id="topup-done" ref={(el) => el?.focus()} tabIndex={-1}>{t('topUp.done')}</h2>
      <Money amount={topUp.amount!} currency={topUp.currency as Currency} size="xl" />
    </section>
  )
  else if (topUp && topUp.status === 'REFUND_NEEDED') body = <Alert tone="warning" title={t('topUp.refund')} />
  else if ((topUp && topUp.status === 'EXPIRED') || cancelled) body = <Alert title={t('topUp.cancelled')} />
  else if (stillPolling) body = <div aria-busy="true"><p role="status">{t('topUp.waiting')}</p><Skeleton height="6rem" /></div>
  else body = <Alert title={t('topUp.slow')} />

  return (
    <main className="page">
      <h1>{t('topUp.title')}</h1>
      {body}
      <div className="transfer-actions">
        <Link className="btn btn-primary" to="/">{t('money.home')}</Link>
        {!stillPolling && <Link className="btn btn-secondary" to="/top-up">{t('topUp.another')}</Link>}
      </div>
    </main>
  )
}
