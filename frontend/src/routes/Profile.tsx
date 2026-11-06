import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api } from '../api/client'
import { t } from '../i18n'
import { Alert, Button, ErrorState, Skeleton, TextField } from '../ui'
import { problemCode } from './money'
import { toPhone } from './phone'
import { ProfilePhoto } from './ProfilePhoto'

/** Today as yyyy-mm-dd in the user's own time zone (toISOString would give the UTC date: wrong in Baku 00:00–04:00). */
const localToday = () => new Date().toLocaleDateString('sv')

// Mirrors EditProfileRequest. The first save needs every field, so the form always asks for all of them.
const schema = z.object({
  fullName: z.string().trim().min(1, t('profile.required')).max(100, t('profile.nameTooLong')),
  phoneNumber: z.string().transform((s, ctx) => toPhone(s) ?? (ctx.addIssue({ code: 'custom', message: t('pay.phoneInvalid') }), z.NEVER)),
  // <input type="date"> gives yyyy-mm-dd; the API wants dd-MM-yyyy
  dateOfBirth: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, t('profile.required'))
    .refine((d) => d < localToday(), t('profile.dobPast'))
    .transform((d) => d.split('-').reverse().join('-')),
  gender: z.enum(['MALE', 'FEMALE'], { error: t('profile.required') }),
})
type Input = z.input<typeof schema>
type Form = z.output<typeof schema>

const toInputDate = (d?: string) => (d && /^\d{2}-\d{2}-\d{4}$/.test(d) ? d.split('-').reverse().join('-') : '')

export default function Profile() {
  const [state, setState] = useState<'loading' | 'ready' | 'failed'>('loading')
  const [round, setRound] = useState(0)
  const [email, setEmail] = useState('')
  const [photo, setPhoto] = useState<string | null>(null)
  const [saved, setSaved] = useState(false) // the server keeps a photo only for a saved profile
  const [name, setName] = useState('')
  const [result, setResult] = useState<'saved' | 'failed' | 'invalid' | null>(null)
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } =
    useForm<Input, unknown, Form>({ resolver: zodResolver(schema), defaultValues: { fullName: '', phoneNumber: '', dateOfBirth: '', gender: undefined } })

  useEffect(() => {
    let live = true
    void api.GET('/api/profile/me')
      .then(({ data, response }) => {
        if (!live) return
        const p = data?.data
        if (!response.ok || !p) { setState('failed'); return }
        setEmail(p.email ?? '')
        setPhoto(p.photoUrl ?? null)
        setSaved(Boolean(p.phoneNumber))
        setName(p.fullName ?? '')
        reset({ fullName: p.fullName ?? '', phoneNumber: p.phoneNumber ?? '', dateOfBirth: toInputDate(p.dateOfBirth), gender: p.gender })
        setState('ready')
      })
      .catch(() => { if (live) setState('failed') })
    return () => { live = false }
  }, [round, reset])

  const save = async (form: Form) => {
    setResult(null)
    try {
      const { response, error } = await api.POST('/api/profile', { body: form })
      setResult(response.ok ? 'saved' : problemCode(error) === 'VALIDATION_FAILED' ? 'invalid' : 'failed')
      if (response.ok) { setSaved(true); setName(form.fullName) }
    } catch {
      setResult('failed')
    }
  }

  return (
    <main className="page">
      <h1>{t('profile.title')}</h1>
      {state === 'loading' ? (
        <div aria-busy="true"><Skeleton height="20rem" /></div>
      ) : state === 'failed' ? (
        <ErrorState title={t('profile.loadFailed')}
          action={<Button variant="secondary" onClick={() => { setState('loading'); setRound((r) => r + 1) }}>{t('error.retry')}</Button>} />
      ) : (
        <>
        <ProfilePhoto name={name} url={photo} saved={saved} onChange={setPhoto} />
        <form className="auth-form" onSubmit={(e) => void handleSubmit(save)(e)} noValidate>
          {result === 'saved' && <Alert tone="success" title={t('profile.saved')} />}
          {result === 'invalid' && <Alert tone="danger" title={t('profile.invalid')} />}
          {result === 'failed' && <Alert tone="danger" title={t('profile.failed')} />}
          <TextField label={t('profile.email')} value={email} readOnly />
          <TextField label={t('profile.fullName')} autoComplete="name" error={errors.fullName?.message} {...register('fullName')} />
          <TextField label={t('pay.phone')} type="tel" inputMode="tel" autoComplete="tel" placeholder="+994 50 123 45 67"
            error={errors.phoneNumber?.message} {...register('phoneNumber')} />
          <TextField label={t('profile.dob')} type="date" autoComplete="bday" max={localToday()} error={errors.dateOfBirth?.message} {...register('dateOfBirth')} />
          <fieldset className="field">
            <legend className="field-label">{t('profile.gender')}</legend>
            <div className="radio-row">
              <label><input type="radio" value="FEMALE" aria-invalid={errors.gender ? true : undefined} aria-describedby={errors.gender ? 'gender-error' : undefined} {...register('gender')} /> {t('profile.female')}</label>
              <label><input type="radio" value="MALE" aria-invalid={errors.gender ? true : undefined} aria-describedby={errors.gender ? 'gender-error' : undefined} {...register('gender')} /> {t('profile.male')}</label>
            </div>
            {errors.gender && <p id="gender-error" className="field-error">{errors.gender.message}</p>}
          </fieldset>
          <Button type="submit" block loading={isSubmitting}>{t('profile.save')}</Button>
        </form>
        </>
      )}
      <nav className="home-section" aria-labelledby="security">
        <h2 id="security">{t('profile.security')}</h2>
        <ul className="settings-links">
          <li><Link to="/settings/email">{t('changeEmail.link')}</Link></li>
          <li><Link to="/settings/pin">{t('changePin.link')}</Link></li>
          <li><Link to="/settings/sessions">{t('sessions.link')}</Link></li>
          <li><Link to="/settings/verify">{t('verify.link')}</Link></li>
        </ul>
      </nav>
      <nav className="home-section" aria-labelledby="help">
        <h2 id="help">{t('profile.help')}</h2>
        <ul className="settings-links">
          <li><Link to="/help/report">{t('report.link')}</Link></li>
          <li><Link to="/legal/terms">{t('signUp.terms')}</Link></li>
          <li><Link to="/legal/privacy">{t('signUp.privacy')}</Link></li>
        </ul>
      </nav>
    </main>
  )
}
