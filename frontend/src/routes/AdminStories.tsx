import { useEffect, useId, useState } from 'react'
import { api } from '../api/client'
import type { components } from '../api/schema'
import { t } from '../i18n'
import { Alert, Button, Dialog, EmptyState, ErrorState, Skeleton } from '../ui'
import { formatWhen } from './kyc'
import { problemCode } from './money'

type Promo = components['schemas']['PromoStoryResponse']

const TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'video/mp4', 'video/quicktime', 'video/webm']
const MAX_BYTES = 10 * 1024 * 1024 // spring.servlet.multipart.max-file-size
const DURATIONS = [6, 24, 72, 168]

/** Admin: publish promo stories every user sees on the dashboard, and take live ones down (/api/admin/stories). */
export default function AdminStories() {
  const [promos, setPromos] = useState<Promo[] | null | 'failed'>(null)
  const [round, setRound] = useState(0)
  const [notice, setNotice] = useState<string | null>(null)
  const [removing, setRemoving] = useState<Promo | null>(null)
  const [removeBusy, setRemoveBusy] = useState(false)
  const [removeError, setRemoveError] = useState<string | null>(null)

  useEffect(() => {
    let live = true
    void api.GET('/api/admin/stories')
      .then(({ data, response }) => { if (live) setPromos(response.ok && Array.isArray(data?.data) ? data.data : 'failed') })
      .catch(() => { if (live) setPromos('failed') })
    return () => { live = false }
  }, [round])
  const reload = () => setRound((r) => r + 1)

  const takeDown = async () => {
    if (!removing?.id) return
    setRemoveBusy(true); setRemoveError(null)
    try {
      const { response } = await api.DELETE('/api/admin/stories/{storyId}', { params: { path: { storyId: removing.id } } })
      // 404: already taken down (another admin) — the outcome asked for
      if (response.ok || response.status === 404) {
        setPromos((list) => (Array.isArray(list) ? list.filter((p) => p.id !== removing.id) : list))
        setRemoving(null)
        setNotice(t('promo.removed'))
      } else setRemoveError(t('promo.removeFailed'))
    } catch {
      setRemoveError(t('promo.removeFailed'))
    } finally {
      setRemoveBusy(false)
    }
  }

  return (
    <main className="page">
      <h1>{t('promo.title')}</h1>
      {notice && <Alert tone="success" title={notice} />}
      <PublishForm onPublished={() => { setNotice(t('promo.published')); reload() }} />
      <section className="home-section" aria-labelledby="live">
        <h2 id="live">{t('promo.live')}</h2>
        {promos === null ? <Skeleton height="8rem" />
          : promos === 'failed' ? <ErrorState title={t('promo.loadFailed')}
              action={<Button variant="secondary" onClick={() => { setPromos(null); reload() }}>{t('error.retry')}</Button>} />
          : promos.length === 0 ? <EmptyState title={t('promo.none')} />
          : (
            <ul className="promo-list">
              {promos.map((p) => (
                <li key={p.id}>
                  {p.mediaType === 'VIDEO'
                    ? <video className="promo-thumb" src={p.mediaUrl} muted playsInline preload="metadata" aria-label={t('promo.video')} />
                    : <img className="promo-thumb" src={p.mediaUrl} alt={t('promo.image')} />}
                  <span className="promo-meta">
                    <span>{t('promo.views', { n: p.views ?? 0 })}</span>
                    <span className="muted">{t('promo.until', { when: formatWhen(p.expiresAt) })}</span>
                  </span>
                  <Button variant="secondary" size="sm" onClick={() => { setRemoveError(null); setRemoving(p) }}>{t('promo.remove')}</Button>
                </li>
              ))}
            </ul>
          )}
      </section>
      <Dialog open={removing !== null} onClose={() => setRemoving(null)} title={t('promo.removeTitle')} dismissable={!removeBusy}
        actions={<>
          <Button variant="secondary" disabled={removeBusy} onClick={() => setRemoving(null)}>{t('sessions.keep')}</Button>
          <Button variant="danger" loading={removeBusy} onClick={() => void takeDown()}>{t('promo.remove')}</Button>
        </>}>
        {removeError && <Alert tone="danger" title={removeError} />}
        <p>{t('promo.removeBody')}</p>
      </Dialog>
    </main>
  )
}

function PublishForm({ onPublished }: { onPublished: () => void }) {
  const [file, setFile] = useState<File | null>(null)
  const [hours, setHours] = useState(24)
  const [fileError, setFileError] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [formKey, setFormKey] = useState(0) // remounts the file input after a publish, so it is empty again
  const fileId = useId()

  const pick = (f: File | undefined) => {
    setError(null)
    setFile(null)
    if (!f) return setFileError(null)
    if (!TYPES.includes(f.type)) return setFileError(t('promo.fileType'))
    if (f.size > MAX_BYTES) return setFileError(t('promo.fileTooLarge'))
    setFileError(null)
    setFile(f)
  }

  const publish = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!file) return setFileError(fileError ?? t('promo.fileRequired'))
    setBusy(true); setError(null)
    const form = new FormData()
    form.append('file', file)
    try {
      const { error: problem, response } = await api.POST('/api/admin/stories', {
        params: { query: { hours } },
        body: form as never, // multipart: sent as built, the browser sets the boundary
        bodySerializer: (b) => b as unknown as FormData,
      })
      if (response.ok) { setFile(null); setFormKey((k) => k + 1); return onPublished() }
      const code = problemCode(problem)
      setError(t(code === 'MEDIA_UPLOAD_FAILED' ? 'verify.storageDown' : response.status === 413 ? 'promo.fileTooLarge'
        : code === 'VALIDATION_FAILED' ? 'promo.fileType' : 'promo.failed'))
    } catch {
      setError(t('promo.failed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form key={formKey} className="auth-form home-section" noValidate onSubmit={(e) => void publish(e)}>
      <h2>{t('promo.new')}</h2>
      {error && <Alert tone="danger" title={error} />}
      <div className={['field', fileError && 'field-invalid'].filter(Boolean).join(' ')}>
        <label htmlFor={fileId} className="field-label">{t('promo.file')}</label>
        <input id={fileId} className="file-input" type="file" accept={TYPES.join(',')}
          aria-invalid={fileError ? true : undefined} aria-describedby={`${fileId}-${fileError ? 'error' : 'hint'}`}
          onChange={(e) => pick(e.target.files?.[0])} />
        {fileError
          ? <p id={`${fileId}-error`} className="field-error">{fileError}</p>
          : <p id={`${fileId}-hint`} className="field-hint">{t('promo.fileHint')}</p>}
      </div>
      <div className="field">
        <label htmlFor="promo-hours" className="field-label">{t('promo.duration')}</label>
        <div className="field-control">
          <select id="promo-hours" value={hours} onChange={(e) => setHours(Number(e.target.value))}>
            {DURATIONS.map((h) => <option key={h} value={h}>{t(h < 48 ? 'promo.hours' : 'promo.days', { n: h < 48 ? h : h / 24 })}</option>)}
          </select>
        </div>
      </div>
      <Button type="submit" block loading={busy}>{t('promo.publish')}</Button>
    </form>
  )
}
