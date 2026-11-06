import { useId, useRef, useState } from 'react'
import { api } from '../api/client'
import { t, type MessageKey } from '../i18n'
import { Alert, Avatar, Button } from '../ui'
import { problemCode } from './money'

const TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
const MAX_BYTES = 5 * 1024 * 1024 // EditProfileService.MAX_PHOTO_BYTES

/**
 * Profile photo: shows it (a 10-minute link from GET /api/v1/profile/me), replaces it (POST /api/v1/profile/photo) or
 * removes it (DELETE /api/v1/profile/photo). The server needs a saved profile first.
 */
export function ProfilePhoto({ name, url, saved, onChange }:
  { name: string; url: string | null; saved: boolean; onChange: (url: string | null) => void }) {
  const [busy, setBusy] = useState<'upload' | 'remove' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [broken, setBroken] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const inputId = useId()

  const upload = async (file: File | undefined) => {
    if (input.current) input.current.value = '' // the same file can be picked again after an error
    if (!file) return
    setError(null)
    if (!TYPES.includes(file.type) || file.size === 0) return setError(t('photo.type'))
    if (file.size > MAX_BYTES) return setError(t('photo.tooLarge'))
    setBusy('upload')
    const form = new FormData()
    form.append('file', file)
    try {
      const { data, error: problem, response } = await api.POST('/api/v1/profile/photo', {
        body: form as never, // multipart: sent as built, the browser sets the boundary
        bodySerializer: (b) => b as unknown as FormData,
      })
      if (response.ok) { setBroken(false); onChange(data?.data?.photoUrl ?? null); return }
      const messages: Record<string, MessageKey> = {
        PROFILE_REQUIRED: 'photo.saveFirst', VALIDATION_FAILED: 'photo.type', PHOTO_TOO_LARGE: 'photo.tooLarge',
        PAYLOAD_TOO_LARGE: 'photo.tooLarge', MEDIA_UPLOAD_FAILED: 'verify.storageDown',
      }
      setError(t(messages[problemCode(problem) ?? ''] ?? 'photo.failed'))
    } catch {
      setError(t('photo.failed'))
    } finally {
      setBusy(null)
    }
  }

  const remove = async () => {
    setError(null); setBusy('remove')
    try {
      const { response } = await api.DELETE('/api/v1/profile/photo')
      if (response.ok) onChange(null)
      else setError(t('photo.failed'))
    } catch {
      setError(t('photo.failed'))
    } finally {
      setBusy(null)
    }
  }

  return (
    <div className="profile-photo">
      {url && !broken
        ? <img className="profile-photo-img" src={url} alt={t('photo.alt')} onError={() => setBroken(true)} />
        : <span className="profile-photo-fallback"><Avatar name={name || '?'} /></span>}
      <div className="profile-photo-actions">
        {/* a real file input, visually a button: keyboard and screen readers get the native control */}
        <input ref={input} id={inputId} className="sr-only" type="file" accept={TYPES.join(',')} disabled={!saved || busy !== null}
          onChange={(e) => void upload(e.target.files?.[0])} />
        <label htmlFor={inputId} className={`btn btn-secondary btn-sm${!saved || busy ? ' is-disabled' : ''}`}
          aria-busy={busy === 'upload' || undefined}>
          {t(url ? 'photo.change' : 'photo.add')}
        </label>
        {url && <Button variant="ghost" size="sm" loading={busy === 'remove'} disabled={busy !== null} onClick={() => void remove()}>{t('photo.remove')}</Button>}
        {!saved && <p className="field-hint">{t('photo.saveFirst')}</p>}
      </div>
      {error && <Alert tone="danger" title={error} />}
    </div>
  )
}
