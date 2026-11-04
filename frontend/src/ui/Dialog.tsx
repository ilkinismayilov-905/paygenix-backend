import { useEffect, useId, useRef, type ReactNode } from 'react'

type Props = { open: boolean; onClose: () => void; title: string; children: ReactNode; actions?: ReactNode }

/** Native <dialog>: focus trap, Escape and the top layer come from the browser. onClose fires once per close. */
export function Dialog({ open, onClose, title, children, actions }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
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
      aria-labelledby={titleId}
      onClose={onClose}
      // The box has no padding (it is on .dialog-inner), so only a backdrop click targets the <dialog> itself.
      onClick={(e) => { if (e.target === ref.current) ref.current.close() }}
    >
      <div className="dialog-inner">
        <h2 id={titleId}>{title}</h2>
        <div className="dialog-body">{children}</div>
        {actions && <div className="dialog-actions">{actions}</div>}
      </div>
    </dialog>
  )
}
