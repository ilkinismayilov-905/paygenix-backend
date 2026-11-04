import { useId, type InputHTMLAttributes, type ReactNode } from 'react'

type Props = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  hint?: ReactNode
  error?: string
  /** Text or symbol shown after the value, such as a currency. */
  suffix?: ReactNode
}

export function TextField({ label, hint, error, suffix, id, className, ...rest }: Props) {
  const autoId = useId()
  const inputId = id ?? autoId
  const describedBy = error ? `${inputId}-error` : hint ? `${inputId}-hint` : undefined
  return (
    <div className={['field', error && 'field-invalid', className].filter(Boolean).join(' ')}>
      <label htmlFor={inputId} className="field-label">{label}</label>
      <div className="field-control">
        <input id={inputId} aria-invalid={error ? true : undefined} aria-describedby={describedBy} {...rest} />
        {suffix && <span className="field-suffix">{suffix}</span>}
      </div>
      {error ? (
        <p id={`${inputId}-error`} className="field-error">{error}</p>
      ) : hint ? (
        <p id={`${inputId}-hint`} className="field-hint">{hint}</p>
      ) : null}
    </div>
  )
}
