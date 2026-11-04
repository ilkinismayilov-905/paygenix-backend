import { useId, useRef, type ClipboardEvent, type KeyboardEvent } from 'react'

type Props = {
  label: string
  value: string
  onChange: (value: string) => void
  length?: number
  error?: string
  autoFocus?: boolean
}

/** One box per digit. Digits are masked, typing moves forward, Backspace moves back, paste fills all boxes. */
export function PinInput({ label, value, onChange, length = 6, error, autoFocus }: Props) {
  const id = useId()
  const refs = useRef<(HTMLInputElement | null)[]>([])
  const focus = (i: number) => refs.current[Math.max(0, Math.min(length - 1, i))]?.focus()

  // Clearing a middle box closes the gap (later digits shift left), so the value is always a prefix.
  const setDigit = (i: number, digit: string) => {
    const chars = value.padEnd(length, ' ').split('')
    chars[i] = digit || ' '
    onChange(chars.join('').trimEnd().replace(/ /g, ''))
  }

  const onKeyDown = (i: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !value[i]) { e.preventDefault(); setDigit(i - 1, ''); focus(i - 1) }
    else if (e.key === 'ArrowLeft') focus(i - 1)
    else if (e.key === 'ArrowRight') focus(i + 1)
  }

  const onPaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault()
    const digits = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, length)
    if (!digits) return
    onChange(digits)
    focus(digits.length)
  }

  return (
    <fieldset className={['pin', error && 'pin-invalid'].filter(Boolean).join(' ')} aria-describedby={error ? `${id}-error` : undefined}>
      <legend className="field-label">{label}</legend>
      <div className="pin-boxes">
        {Array.from({ length }, (_, i) => (
          <input
            key={i}
            ref={(el) => { refs.current[i] = el }}
            type="password"
            inputMode="numeric"
            autoComplete="off"
            aria-label={`Digit ${i + 1} of ${length}`}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${id}-error` : undefined}
            autoFocus={autoFocus && i === 0}
            value={value[i] ?? ''}
            onChange={(e) => {
              const d = e.target.value.replace(/\D/g, '').slice(-1)
              // Only fill boxes in order, so the value never has gaps.
              if (d && i > value.length) { setDigit(value.length, d); focus(value.length + 1); return }
              setDigit(i, d)
              if (d) focus(i + 1)
            }}
            onKeyDown={(e) => onKeyDown(i, e)}
            onPaste={onPaste}
            onFocus={(e) => e.target.select()}
          />
        ))}
      </div>
      {error && <p id={`${id}-error`} className="field-error">{error}</p>}
    </fieldset>
  )
}
