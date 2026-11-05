import { zodResolver } from '@hookform/resolvers/zod'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { t, type MessageKey } from '../i18n'
import { Button, PinInput } from '../ui'

const pinSchema = z.object({ pin: z.string().regex(/^\d{6}$/, t('signUp.pinInvalid')) })

/** One PIN entry (sign-up, first PIN). PinInput is masked, numeric and autocomplete="off"; no show/hide by design (owner, T-122). */
export function PinStep({ label, submit, onSubmit }: { label: MessageKey; submit: MessageKey; onSubmit: (pin: string) => void | Promise<void> }) {
  const { control, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(pinSchema), defaultValues: { pin: '' } })
  return (
    <form className="auth-form" noValidate onSubmit={handleSubmit((f) => onSubmit(f.pin))}>
      <Controller control={control} name="pin" render={({ field }) => (
        <PinInput ref={field.ref} label={t(label)} value={field.value} onChange={field.onChange} error={errors.pin?.message} autoFocus />
      )} />
      <p className="muted">{t('signUp.pinHint')}</p>
      <Button type="submit" block loading={isSubmitting}>{t(submit)}</Button>
    </form>
  )
}
