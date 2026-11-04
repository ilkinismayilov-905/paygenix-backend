import type { ReactNode } from 'react'

type Props = { tone?: 'info' | 'success' | 'warning' | 'danger'; title?: string; children?: ReactNode; action?: ReactNode }

const icons = { info: 'i', success: '✓', warning: '!', danger: '!' }

export function Alert({ tone = 'info', title, children, action }: Props) {
  return (
    // Errors interrupt screen readers; the rest are announced politely.
    <div className={`alert alert-${tone}`} role={tone === 'danger' ? 'alert' : 'status'}>
      <span className="alert-icon" aria-hidden="true">{icons[tone]}</span>
      <div className="alert-body">
        {title && <p className="alert-title">{title}</p>}
        {children && <div>{children}</div>}
      </div>
      {action}
    </div>
  )
}
