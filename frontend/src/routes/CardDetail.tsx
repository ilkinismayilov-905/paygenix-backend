import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, CardTile, Dialog, EmptyState, ErrorState, Skeleton, type Currency } from '../ui'
import { useActivity } from './activity'
import { ActivitySection } from './ActivitySection'
import { problemCode, type Card } from './money'

/** One own card: null while loading, 'missing' on 404 (not ours, or closed), 'failed' on any other error. */
function useCard(id: number) {
  const [card, setCard] = useState<Card | null | 'missing' | 'failed'>(null)
  const [round, setRound] = useState(0)
  useEffect(() => {
    let live = true
    void api.GET('/api/cards/{id}', { params: { path: { id } } })
      .then(({ data, response }) => {
        if (!live) return
        const c = data?.data
        setCard(response.status === 404 ? 'missing' : response.ok && c?.id && c.amount && c.currency ? c : 'failed')
      })
      .catch(() => { if (live) setCard('failed') })
    return () => { live = false }
  }, [id, round])
  return { card, retry: () => { setCard(null); setRound((r) => r + 1) } }
}

function Missing() {
  return (
    <main className="page">
      <EmptyState title={t('card.notFound')} />
      <Link to="/cards">{t('card.back')}</Link>
    </main>
  )
}

// Remounts per card, so no state of the previous card survives /cards/1 → /cards/2.
export default function CardDetailRoute() {
  const raw = useParams().id ?? ''
  return /^\d{1,18}$/.test(raw) ? <CardDetail key={raw} id={Number(raw)} /> : <Missing />
}

function CardDetail({ id }: { id: number }) {
  const { card, retry: retryCard } = useCard(id)
  const activity = useActivity(id)
  const retry = () => { retryCard(); activity.retry() }

  const navigate = useNavigate()
  const [asking, setAsking] = useState(false)
  const [closing, setClosing] = useState(false)
  const [closeError, setCloseError] = useState<string | null>(null)
  const close = async () => {
    setClosing(true); setCloseError(null)
    try {
      const { error: problem, response } = await api.DELETE('/api/cards/{id}', { params: { path: { id } } })
      // 404: closed already (another tab) — the outcome the user asked for
      if (response.ok || response.status === 404) { navigate('/cards', { replace: true }); return }
      setCloseError(problemCode(problem) === 'CARD_HAS_BALANCE' ? t('card.hasBalance') : t('card.closeFailed'))
    } catch {
      setCloseError(t('card.closeFailed'))
    } finally {
      setClosing(false)
    }
  }

  if (card === 'missing') return <Missing />
  if (card === 'failed') {
    return (
      <main className="page">
        <ErrorState title={t('card.failed')} action={<Button variant="secondary" onClick={retry}>{t('error.retry')}</Button>} />
      </main>
    )
  }
  if (card === null) return <main className="page" aria-busy="true"><Skeleton height="14rem" radius="var(--radius-lg)" /></main>

  const last4 = card.maskedNumber?.replace(/\D/g, '').slice(-4) ?? ''
  return (
    <main className="page">
      <Link to="/cards">{t('card.back')}</Link>
      <h1>{t('card.title', { last4 })}</h1>
      <CardTile maskedNumber={card.maskedNumber ?? ''} holder={card.cardHolderName ?? ''} expires={card.expirationDate}
        network={card.cardNetwork ?? ''} currency={card.currency as Currency} balance={card.amount!} />

      {activity.items === 'failed'
        ? <ErrorState title={t('card.activityFailed')} action={<Button variant="secondary" onClick={activity.retry}>{t('error.retry')}</Button>} />
        : <ActivitySection activity={activity} />}

      <Button variant="danger" onClick={() => { setCloseError(null); setAsking(true) }}>{t('card.close')}</Button>
      <Dialog open={asking} onClose={() => setAsking(false)} title={t('card.closeTitle')} dismissable={!closing}
        actions={<>
          <Button variant="secondary" onClick={() => setAsking(false)}>{t('card.closeKeep')}</Button>
          <Button variant="danger" loading={closing} onClick={() => void close()}>{t('card.close')}</Button>
        </>}>
        {closeError && <Alert tone="danger" title={closeError} />}
        <p>{t('card.closeBody')}</p>
      </Dialog>
    </main>
  )
}
