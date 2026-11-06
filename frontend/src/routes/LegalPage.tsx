import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { api } from '../api/client'
import { useAuthStatus } from '../auth/session'
import { t } from '../i18n'
import { Button, EmptyState, ErrorState, Skeleton } from '../ui'
import { Logo } from './Logo'

type Doc = { version: string; effectiveDate?: string; content: string }

const date = new Intl.DateTimeFormat('en', { dateStyle: 'long', timeZone: 'UTC' })
const formatDate = (d?: string) => (d && !Number.isNaN(Date.parse(d)) ? date.format(new Date(d)) : '')

/** Terms or Privacy as a page anyone can open (GET /api/legal/terms, /privacy); the sign-up dialogs show the same text. */
function LegalPage({ type }: { type: 'terms' | 'privacy' }) {
  const [doc, setDoc] = useState<Doc | null | 'missing' | 'failed'>(null)
  const [round, setRound] = useState(0)
  const signedIn = useAuthStatus() === 'signed-in'
  useEffect(() => {
    let live = true
    void api.GET(type === 'terms' ? '/api/legal/terms' : '/api/legal/privacy')
      .then(({ data, response }) => {
        if (!live) return
        const d = data?.data
        if (response.ok && d?.version) setDoc({ version: d.version, effectiveDate: d.effectiveDate, content: d.content ?? '' })
        else setDoc(response.status === 404 ? 'missing' : 'failed')
      })
      .catch(() => { if (live) setDoc('failed') })
    return () => { live = false }
  }, [type, round])

  const title = t(type === 'terms' ? 'signUp.terms' : 'signUp.privacy')
  const other = type === 'terms' ? { to: '/legal/privacy', label: t('signUp.privacy') } : { to: '/legal/terms', label: t('signUp.terms') }
  return (
    <main className="page legal-page">
      <Link to={signedIn ? '/' : '/sign-in'} className="shell-brand"><Logo /> {t('app.name')}</Link>
      <h1>{title}</h1>
      {doc === null ? <div aria-busy="true"><Skeleton height="20rem" /></div>
        : doc === 'missing' ? <EmptyState title={t('signUp.legalMissing')} />
        : doc === 'failed' ? <ErrorState title={t('legalPage.failed')}
            action={<Button variant="secondary" onClick={() => { setDoc(null); setRound((r) => r + 1) }}>{t('error.retry')}</Button>} />
        : <>
            <p className="muted">
              {t('legal.version', { version: doc.version })}
              {doc.effectiveDate && <> · {t('legalPage.effective', { date: formatDate(doc.effectiveDate) })}</>}
            </p>
            <div className="legal-body">{doc.content}</div>
          </>}
      <p><Link to={other.to}>{other.label}</Link></p>
    </main>
  )
}

export const Terms = () => <LegalPage type="terms" />
export const Privacy = () => <LegalPage type="privacy" />
