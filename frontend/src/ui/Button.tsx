import type { ButtonHTMLAttributes } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'md' | 'sm'
  block?: boolean
  loading?: boolean
}

export function Button({ variant = 'primary', size = 'md', block, loading, disabled, className, children, ...rest }: Props) {
  const cls = ['btn', `btn-${variant}`, size === 'sm' && 'btn-sm', block && 'btn-block', className].filter(Boolean).join(' ')
  return (
    <button type="button" {...rest} className={cls} disabled={disabled || loading} aria-busy={loading || undefined}>
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  )
}
