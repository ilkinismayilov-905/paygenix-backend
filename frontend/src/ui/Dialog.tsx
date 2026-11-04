import { useEffect, useRef, type ReactNode } from 'react'

type Props = { open: boolean; onClose: () => void; title: string; children: ReactNode; actions?: ReactNode }

/** Native <dialog>: focus trap, Escape and the top layer come from the browser. */
export function Dialog({ open, onClose, title, children, actions }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const d = ref.current
    if (!d) return
    if (open && !d.open) d.showModal()
    if (!open && d.open) d.close()
  }, [open])
  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-labelledby="dialog-title"
      onClose={onClose}
      onClick={(e) => { if (e.target === ref.current) onClose() }}
    >
      <h2 id="dialog-title">{title}</h2>
      <div className="dialog-body">{children}</div>
      {actions && <div className="dialog-actions">{actions}</div>}
    </dialog>
  )
}
