import type { ReactNode } from 'react'

export function List({ children, label }: { children: ReactNode; label?: string }) {
  return <ul className="list" aria-label={label}>{children}</ul>
}

type RowProps = { leading?: ReactNode; title: ReactNode; subtitle?: ReactNode; trailing?: ReactNode; trailingSub?: ReactNode }

export function ListRow({ leading, title, subtitle, trailing, trailingSub }: RowProps) {
  return (
    <li className="list-row">
      {leading && <span className="list-leading">{leading}</span>}
      <span className="list-main">
        <span className="list-title">{title}</span>
        {subtitle && <span className="list-sub">{subtitle}</span>}
      </span>
      {(trailing || trailingSub) && (
        <span className="list-trailing">
          {trailing}
          {trailingSub && <span className="list-sub">{trailingSub}</span>}
        </span>
      )}
    </li>
  )
}

/** Round initials badge for people and merchants. */
export function Avatar({ name }: { name: string }) {
  const initials = name.split(/\s+/).map((w) => w[0]).join('').slice(0, 2).toUpperCase()
  return <span className="avatar" aria-hidden="true">{initials}</span>
}
