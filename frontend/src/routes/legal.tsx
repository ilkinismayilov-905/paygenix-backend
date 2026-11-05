import { useState } from 'react'
import { createPortal } from 'react-dom'
import { api } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, Dialog } from '../ui'
import { legalTypeOf, loadPendingLegal, useLegalDocs, type LegalDoc, type LegalRef, type LegalType } from './legalDocs'

export function LegalDialog({ type, doc, onClose }: { type: LegalType; doc: LegalDoc | undefined; onClose: () => void }) {
  const text = doc === undefined ? t('signUp.legalLoading')
    : doc === 'missing' ? t('signUp.legalMissing')
    : doc === 'failed' ? t('signUp.failed')
    : doc.content
  return (
    <Dialog open onClose={onClose} title={t(type === 'terms' ? 'signUp.terms' : 'signUp.privacy')}
      actions={<Button variant="secondary" onClick={onClose}>{t('signUp.close')}</Button>}>
      <p className="legal-text">{text}</p>
    </Dialog>
  )
}

/** "By continuing you agree to the Terms and the Privacy Policy", each opening its text. */
export function AgreeLine({ docs }: { docs: Partial<Record<LegalType, LegalDoc>> }) {
  const [open, setOpen] = useState<LegalType | null>(null)
  return (
    <>
      <p className="muted">
        {t('signUp.agree')} <button type="button" className="link" onClick={() => setOpen('terms')}>{t('signUp.terms')}</button>
        {' '}{t('signUp.and')}{' '}<button type="button" className="link" onClick={() => setOpen('privacy')}>{t('signUp.privacy')}</button>.
      </p>
      {/* portaled: the line sits inside a form, the dialog must not */}
      {open && createPortal(<LegalDialog type={open} doc={docs[open]} onClose={() => setOpen(null)} />, document.body)}
    </>
  )
}

/**
 * Shown instead of the app while the signed-in user has not accepted the current versions (new account from
 * Google, or a new version was published). The backend blocks transfers and payments meanwhile.
 */
export function AcceptLegal({ pending, onDone }: { pending: LegalRef[]; onDone: (stillPending: LegalRef[]) => void }) {
  const { docs, reload } = useLegalDocs()
  const [open, setOpen] = useState<LegalType | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const accept = async () => {
    // accept only what the dialogs show: a stale text (server cache) must not be recorded as read
    const stale = pending.some((d) => { const doc = docs[legalTypeOf(d.type)]; return typeof doc !== 'object' || doc.version !== d.version })
    if (stale) { reload(); return setError(t('legal.reload')) }
    setBusy(true)
    setError(null)
    try {
      const { response } = await api.POST('/api/legal/acceptances', { body: { documents: pending } })
      if (response.ok) return onDone([])
      if (response.status === 409) { // a newer version appeared meanwhile: show it
        reload()
        onDone(await loadPendingLegal() ?? pending)
        setError(t('legal.changed'))
      } else setError(t('signUp.failed'))
    } catch {
      setError(t('signUp.failed'))
    }
    setBusy(false)
  }
  return (
    <main className="page">
      <h1>{t('legal.title')}</h1>
      <p className="muted">{t('legal.body')}</p>
      {error && <Alert tone="danger" title={error} />}
      <ul>
        {pending.map((d) => (
          <li key={d.type}>
            <button type="button" className="link" onClick={() => setOpen(legalTypeOf(d.type))}>
              {t(legalTypeOf(d.type) === 'terms' ? 'signUp.terms' : 'signUp.privacy')}
            </button>{' '}<span className="muted">({t('legal.version', { version: d.version })})</span>
          </li>
        ))}
      </ul>
      <Button loading={busy} onClick={() => void accept()}>{t('legal.accept')}</Button>
      {open && <LegalDialog type={open} doc={docs[open]} onClose={() => setOpen(null)} />}
    </main>
  )
}
