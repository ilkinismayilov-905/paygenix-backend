import type { CSSProperties, ReactNode } from 'react'

export function Skeleton({ width = '100%', height = '1em', radius }: { width?: string; height?: string; radius?: string }) {
  return <span className="skeleton" aria-hidden="true" style={{ width, height, borderRadius: radius } as CSSProperties} />
}

type StateProps = { title: string; body?: ReactNode; action?: ReactNode }

export function EmptyState({ title, body, action }: StateProps) {
  return (
    <div className="state">
      <p className="state-title">{title}</p>
      {body && <p className="state-body">{body}</p>}
      {action}
    </div>
  )
}

export function ErrorState({ title, body, action }: StateProps) {
  return (
    <div className="state state-error" role="alert">
      <p className="state-title">{title}</p>
      {body && <p className="state-body">{body}</p>}
      {action}
    </div>
  )
}
