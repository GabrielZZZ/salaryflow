import { useEffect, useRef, useState } from 'react'
import './ConfirmDialog.css'

const FOCUSABLE_SELECTOR = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'

function isFocusable(element: HTMLElement) {
  return element.isConnected
    && element.matches(FOCUSABLE_SELECTOR)
    && !element.matches('[hidden], [aria-hidden="true"], :disabled')
    && !element.closest('[hidden], [aria-hidden="true"], [inert]')
    && element.getClientRects().length > 0
}

interface ConfirmDialogProps {
  open: boolean
  title: string
  message?: string
  confirmLabel: string
  cancelLabel: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmDialog({ open, title, message, confirmLabel, cancelLabel, onConfirm, onCancel }: ConfirmDialogProps) {
  const [rendered, setRendered] = useState(open)
  const [closing, setClosing] = useState(false)
  const cancelButtonRef = useRef<HTMLButtonElement>(null)
  const dialogRef = useRef<HTMLDivElement>(null)
  const previousFocusRef = useRef<HTMLElement | null>(null)
  const onCancelRef = useRef(onCancel)
  const wasOpenRef = useRef(false)

  useEffect(() => {
    onCancelRef.current = onCancel
  }, [onCancel])

  useEffect(() => {
    let timer: number | undefined
    if (open) {
      setRendered(true)
      setClosing(false)
    } else if (rendered) {
      setClosing(true)
      timer = window.setTimeout(() => {
        setRendered(false)
        setClosing(false)
      }, 180)
    }
    return () => { if (timer) window.clearTimeout(timer) }
  }, [open, rendered])

  useEffect(() => {
    if (!open) return
    previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    cancelButtonRef.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCancelRef.current()
        return
      }
      if (event.key !== 'Tab') return
      const dialog = dialogRef.current
      if (!dialog) return
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(isFocusable)
      if (focusable.length === 0) {
        event.preventDefault()
        return
      }
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      const active = document.activeElement
      if (event.shiftKey ? active === first || !dialog.contains(active) : active === last || !dialog.contains(active)) {
        event.preventDefault()
        ;(event.shiftKey ? last : first).focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  useEffect(() => {
    if (open) {
      wasOpenRef.current = true
      return
    }
    if (!wasOpenRef.current || rendered) return
    const previousFocus = previousFocusRef.current
    previousFocusRef.current = null
    wasOpenRef.current = false
    if (previousFocus && isFocusable(previousFocus)) {
      previousFocus.focus()
      return
    }
    Array.from(document.querySelectorAll<HTMLElement>('.page button, .page a[href], .page input, .page select, .page textarea, .page [tabindex]'))
      .find(isFocusable)
      ?.focus()
  }, [open, rendered])

  if (!rendered) return null
  return <div className={`dialog-backdrop${closing ? ' closing' : ''}`} role="presentation" onMouseDown={event => { if (event.currentTarget === event.target && open) onCancel() }}>
    <div ref={dialogRef} className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="confirm-dialog-title" aria-describedby={message ? 'confirm-dialog-message' : undefined}>
      <p className="eyebrow">JUST CHECKING</p>
      <h2 id="confirm-dialog-title">{title}</h2>
      {message && <p id="confirm-dialog-message">{message}</p>}
      <div className="confirm-dialog-actions">
        <button ref={cancelButtonRef} type="button" autoFocus className="dialog-cancel" onClick={onCancel}>{cancelLabel}</button>
        <button type="button" className="dialog-confirm" onClick={onConfirm}>{confirmLabel}</button>
      </div>
    </div>
  </div>
}
