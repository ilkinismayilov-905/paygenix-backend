import { useEffect, useId, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { api, retryAfterMinutes } from '../api/client'
import type { components } from '../api/schema'
import { t, type MessageKey } from '../i18n'
import { Alert, Button, ErrorState, Skeleton } from '../ui'
import { problemCode } from './money'

type Kyc = components['schemas']['KycStatusResponse']
type DocumentType = NonNullable<Kyc['documentType']>

const DOCUMENT_TYPES: DocumentType[] = ['ID_CARD', 'PASSPORT', 'DRIVING_LICENCE']
const PHOTO_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_BYTES = 5 * 1024 * 1024 // KycService.MAX_DOCUMENT_BYTES

const date = new Intl.DateTimeFormat('en', { dateStyle: 'medium' })
const formatDate = (iso?: string) => (iso && !Number.isNaN(Date.parse(iso)) ? date.format(new Date(iso)) : '')

/** Identity verification: one photo of an identity document, reviewed by a person (GET /api/kyc/me, POST /api/kyc). */
export default function Verify() {
  const [kyc, setKyc] = useState<Kyc | null | 'failed'>(null)
  const [round, setRound] = useState(0)
  useEffect(() => {
    let live = true
    void api.GET('/api/kyc/me')
      .then(({ data, response }) => { if (live) setKyc(response.ok && data?.data?.status ? data.data : 'failed') })
      .catch(() => { if (live) setKyc('failed') })
    return () => { live = false }
  }, [round])
  const reload = () => { setKyc(null); setRound((r) => r + 1) }

  return (
    <main className="page">
      <h1>{t('verify.title')}</h1>
      {kyc === null ? <div aria-busy="true"><Skeleton height="16rem" /></div>
        : kyc === 'failed' ? <ErrorState title={t('verify.loadFailed')}
            action={<Button variant="secondary" onClick={reload}>{t('error.retry')}</Button>} />
        : kyc.status === 'APPROVED' ? <Alert tone="success" title={t('verify.approved')} />
        : kyc.status === 'PENDING' ? (
          <Alert title={t('verify.pending')}>{t('verify.pendingBody', { date: formatDate(kyc.submittedAt) })}</Alert>
        ) : (
          <>
            {kyc.status === 'REJECTED'
              ? <Alert tone="warning" title={t('verify.rejected')}>{kyc.rejectionReason}</Alert>
              : <p className="muted">{t('verify.intro')}</p>}
            <SubmitForm onSent={setKyc} onStale={reload} />
          </>
        )}
      <p><Link to="/settings/profile">{t('changePin.back')}</Link></p>
    </main>
  )
}

function SubmitForm({ onSent, onStale }: { onSent: (k: Kyc) => void; onStale: () => void }) {
  const [type, setType] = useState<DocumentType | ''>('')
  const [file, setFile] = useState<File | null>(null)
  const [fieldError, setFieldError] = useState<{ type?: string; file?: string }>({})
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const fileId = useId()

  const preview = useMemo(() => (file ? URL.createObjectURL(file) : null), [file])
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview) }, [preview])

  const pick = (f: File | undefined) => {
    setError(null)
    if (!f) { setFile(null); return }
    if (!PHOTO_TYPES.includes(f.type)) { setFile(null); return setFieldError((e) => ({ ...e, file: t('verify.fileType') })) }
    if (f.size > MAX_BYTES) { setFile(null); return setFieldError((e) => ({ ...e, file: t('verify.fileTooLarge') })) }
    setFieldError((e) => ({ ...e, file: undefined }))
    setFile(f)
  }

  const send = async (e: React.FormEvent) => {
    e.preventDefault()
    const missing = { type: type ? undefined : t('verify.typeRequired'), file: file ? undefined : (fieldError.file ?? t('verify.fileRequired')) }
    setFieldError(missing)
    if (!type || !file) return
    setError(null)
    setBusy(true)
    const form = new FormData() // documentType goes in the query, as the contract says
    form.append('document', file)
    try {
      const { data, error: problem, response } = await api.POST('/api/kyc', {
        params: { query: { documentType: type } },
        body: form as never, // multipart: sent as built, the browser sets the boundary
        bodySerializer: (b) => b as unknown as FormData,
      })
      if (response.ok && data?.data?.status) return onSent(data.data)
      const code = problemCode(problem)
      if (code === 'KYC_ALREADY_PENDING' || code === 'KYC_ALREADY_VERIFIED') return onStale()
      const hours = Math.max(1, Math.ceil((retryAfterMinutes(response) ?? 1440) / 60))
      const messages: Record<string, MessageKey> = {
        DOCUMENT_TOO_LARGE: 'verify.fileTooLarge', PAYLOAD_TOO_LARGE: 'verify.fileTooLarge', VALIDATION_FAILED: 'verify.fileType',
        MEDIA_UPLOAD_FAILED: 'verify.storageDown',
      }
      setError(code === 'KYC_SUBMIT_LIMIT' ? t('verify.limit', { hours }) : t(messages[code ?? ''] ?? 'verify.failed'))
    } catch {
      setError(t('verify.failed'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="auth-form" noValidate onSubmit={(e) => void send(e)}>
      {error && <Alert tone="danger" title={error} />}
      <fieldset className={['field', fieldError.type && 'field-invalid'].filter(Boolean).join(' ')}>
        <legend className="field-label">{t('verify.type')}</legend>
        <div className="radio-row radio-stack">
          {DOCUMENT_TYPES.map((d) => (
            <label key={d}>
              <input type="radio" name="documentType" value={d} checked={type === d}
                aria-invalid={fieldError.type ? true : undefined} aria-describedby={fieldError.type ? 'type-error' : undefined}
                onChange={() => { setType(d); setFieldError((e) => ({ ...e, type: undefined })) }} /> {t(`verify.doc.${d}`)}
            </label>
          ))}
        </div>
        {fieldError.type && <p id="type-error" className="field-error">{fieldError.type}</p>}
      </fieldset>
      <div className={['field', fieldError.file && 'field-invalid'].filter(Boolean).join(' ')}>
        <label htmlFor={fileId} className="field-label">{t('verify.photo')}</label>
        <input id={fileId} className="file-input" type="file" accept={PHOTO_TYPES.join(',')}
          aria-invalid={fieldError.file ? true : undefined} aria-describedby={`${fileId}-${fieldError.file ? 'error' : 'hint'}`}
          onChange={(e) => pick(e.target.files?.[0])} />
        {fieldError.file
          ? <p id={`${fileId}-error`} className="field-error">{fieldError.file}</p>
          : <p id={`${fileId}-hint`} className="field-hint">{t('verify.photoHint')}</p>}
        {preview && <img className="doc-preview" src={preview} alt={t('verify.previewAlt')} />}
      </div>
      <Button type="submit" block loading={busy}>{t('verify.submit')}</Button>
    </form>
  )
}
