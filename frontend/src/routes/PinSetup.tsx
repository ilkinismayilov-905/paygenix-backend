import { useState } from 'react'
import { Link, useLocation } from 'react-router'
import { api } from '../api/client'
import { safeReturnPath } from '../auth/returnPath'
import { t } from '../i18n'
import { Alert } from '../ui'
import { problemCode } from './money'
import { PinStep } from './PinStep'

type Step = 'pin' | 'pinRepeat' | 'done' | 'alreadySet'

/** First PIN for accounts created without one (Google sign-up): create → repeat → POST /api/v1/user/pin. */
export default function PinSetup() {
  const from = safeReturnPath((useLocation().state as { from?: unknown } | null)?.from)
  const [step, setStep] = useState<Step>('pin')
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)

  const save = async (repeat: string) => {
    setError(null)
    if (repeat !== pin) { setStep('pin'); setError(t('signUp.pinMismatch')); return }
    try {
      const { error: problem, response } = await api.POST('/api/v1/user/pin', { body: { pin } })
      if (response.ok) { setStep('done'); return }
      if (problemCode(problem) === 'PIN_ALREADY_SET') { setStep('alreadySet'); return }
      if (problemCode(problem) === 'VALIDATION_FAILED') { setStep('pin'); setError(t('signUp.pinInvalid')); return }
      setError(t('pinSetup.failed'))
    } catch {
      setError(t('pinSetup.failed')) // the repeat step stays, so pressing the button again retries
    }
  }

  if (step === 'done' || step === 'alreadySet') {
    return (
      <main className="page">
        <h1 ref={(el) => el?.focus()} tabIndex={-1}>{t(step === 'done' ? 'pinSetup.done' : 'pinSetup.alreadySet')}</h1>
        <Link className="btn btn-primary" to={from} replace>{t('pinSetup.continue')}</Link>
      </main>
    )
  }

  return (
    <main className="page">
      <h1>{t(step === 'pin' ? 'pinSetup.title' : 'pinSetup.repeatTitle')}</h1>
      {error && <Alert tone="danger" title={error} />}
      {step === 'pin'
        ? <PinStep key="pin" label="signUp.pin" submit="signUp.continue" onSubmit={(p) => { setError(null); setPin(p); setStep('pinRepeat') }} />
        : <PinStep key="repeat" label="signUp.pinRepeat" submit="pinSetup.link" onSubmit={save} />}
    </main>
  )
}
