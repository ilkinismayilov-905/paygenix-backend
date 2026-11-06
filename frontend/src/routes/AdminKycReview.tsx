import { useEffect, useId, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { api } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, Dialog, EmptyState, ErrorState, Skeleton } from '../ui'
import { formatWhen, type Review } from './kyc'
import { problemCode } from './money'

const MAX_REASON = 500

// Remounts per submission, so nothing of the previous one survives /admin/kyc/1 → /admin/kyc/2.
export default function AdminKycReviewRoute() {
  const raw = useParams().id ?? ''
  return /^\d{1,18}$/.test(raw) ? <AdminKycReview key={raw} id={Number(raw)} /> : <Missing />
}

function Missing() {
  return (
    <main className="page">
      <EmptyState title={t('admin.kycMissing')} />
      <Link to="/admin/kyc">{t('admin.back')}</Link>
    </main>
  )
}

/** One submission: who sent it, the document photo (5-minute link), approve or reject with a reason. */
function AdminKycReview({ id }: { id: number }) {
  const navigate = useNavigate()
  const [item, setItem] = useState<Review | null | 'missing' | 'failed'>(null)
  const [round, setRound] = useState(0)
  const [photoFailed, setPhotoFailed] = useState(false)
  const [busy, setBusy] = useState<'approve' | 'reject' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string | null>(null)
  const reasonId = useId()

  useEffect(() => {
    let live = true
    void api.GET('/api/admin/kyc/{id}', { params: { path: { id } } })
      .then(({ data, response }) => {
        if (!live) return
        if (response.status === 404) setItem('missing')
        else setItem(response.ok && data?.data?.id ? data.data : 'failed')
      })
      .catch(() => { if (live) setItem('failed') })
    return () => { live = false }
  }, [id, round])
  // a new link each time: the old one may have expired
  const reload = () => { setPhotoFailed(false); setItem(null); setRound((r) => r + 1) }

  const decide = async (action: 'approve' | 'reject') => {
    if (action === 'reject') {
      const r = reason.trim()
      if (!r) return setReasonError(t('admin.reasonRequired'))
      if (r.length > MAX_REASON) return setReasonError(t('signUp.tooLong', { max: MAX_REASON }))
    }
    setBusy(action); setError(null)
    try {
      const params = { params: { path: { id } } }
      const { error: problem, response } = action === 'approve'
        ? await api.POST('/api/admin/kyc/{id}/approve', params)
        : await api.POST('/api/admin/kyc/{id}/reject', { ...params, body: { reason: reason.trim() } })
      if (response.ok) {
        const name = typeof item === 'object' && item ? item.fullName || item.username || '' : ''
        return navigate('/admin/kyc', { replace: true, state: { reviewed: t(action === 'approve' ? 'admin.approved' : 'admin.rejected', { name }) } })
      }
      const code = problemCode(problem)
      setRejecting(false)
      if (code === 'KYC_ALREADY_REVIEWED') { reload(); return setError(t('admin.alreadyReviewed')) }
      setError(t(code === 'KYC_OWN_SUBMISSION' ? 'admin.own' : code === 'RESOURCE_BUSY' ? 'admin.busy' : 'admin.decideFailed'))
    } catch {
      setError(t('admin.decideFailed'))
    } finally {
      setBusy(null)
    }
  }

  if (item === 'missing') return <Missing />
  if (item === 'failed') {
    return (
      <main className="page">
        <ErrorState title={t('admin.kycFailed')} action={<Button variant="secondary" onClick={reload}>{t('error.retry')}</Button>} />
      </main>
    )
  }
  if (item === null) return <main className="page" aria-busy="true"><Skeleton height="24rem" /></main>

  return (
    <main className="page">
      <Link to="/admin/kyc">{t('admin.back')}</Link>
      <h1>{item.fullName || item.username}</h1>
      {error && <Alert tone="danger" title={error} />}
      <dl className="summary">
        <dt>{t('admin.username')}</dt><dd>@{item.username}</dd>
        <dt>{t('profile.email')}</dt><dd>{item.email}</dd>
        <dt>{t('verify.type')}</dt><dd>{t(`verify.doc.${item.documentType ?? 'ID_CARD'}`)}</dd>
        <dt>{t('admin.sentAt')}</dt><dd>{formatWhen(item.submittedAt)}</dd>
        <dt>{t('admin.statusLabel')}</dt><dd>{t(`admin.status.${item.status ?? 'PENDING'}`)}</dd>
        {item.reviewedAt && <><dt>{t('admin.reviewedAt')}</dt><dd>{formatWhen(item.reviewedAt)}</dd></>}
        {item.rejectionReason && <><dt>{t('admin.reason')}</dt><dd>{item.rejectionReason}</dd></>}
      </dl>
      {photoFailed || !item.documentUrl
        ? <ErrorState title={t('admin.photoFailed')} action={<Button variant="secondary" onClick={reload}>{t('error.retry')}</Button>} />
        : <img className="doc-photo" src={item.documentUrl} alt={t('admin.photoAlt', { name: item.fullName || item.username || '' })}
            referrerPolicy="no-referrer" onError={() => setPhotoFailed(true)} />}
      {item.status === 'PENDING' && (
        <div className="transfer-actions">
          <Button loading={busy === 'approve'} disabled={busy !== null} onClick={() => void decide('approve')}>{t('admin.approve')}</Button>
          <Button variant="danger" disabled={busy !== null} onClick={() => { setReasonError(null); setRejecting(true) }}>{t('admin.reject')}</Button>
        </div>
      )}
      <Dialog open={rejecting} onClose={() => setRejecting(false)} title={t('admin.rejectTitle')} dismissable={busy === null}
        actions={<>
          <Button variant="secondary" disabled={busy !== null} onClick={() => setRejecting(false)}>{t('sessions.keep')}</Button>
          <Button variant="danger" loading={busy === 'reject'} onClick={() => void decide('reject')}>{t('admin.reject')}</Button>
        </>}>
        <div className={['field', reasonError && 'field-invalid'].filter(Boolean).join(' ')}>
          <label htmlFor={reasonId} className="field-label">{t('admin.reasonLabel')}</label>
          <div className="field-control field-control-area">
            <textarea id={reasonId} rows={4} maxLength={MAX_REASON} value={reason} aria-invalid={reasonError ? true : undefined}
              aria-describedby={`${reasonId}-${reasonError ? 'error' : 'hint'}`} onChange={(e) => { setReason(e.target.value); setReasonError(null) }} />
          </div>
          {reasonError
            ? <p id={`${reasonId}-error`} className="field-error">{reasonError}</p>
            : <p id={`${reasonId}-hint`} className="field-hint">{t('admin.reasonHint')}</p>}
        </div>
      </Dialog>
    </main>
  )
}
