/**
 * ToastContainer — renders active toast notifications with enter/exit animations.
 *
 * Subscribes to the toast store for active toasts and renders them positioned
 * in the bottom-left corner of the video area, above the BottomBar.
 * Includes an aria-live region for screen reader announcements.
 *
 * Requirements: 5.4, 5.5, 5.6, 5.7
 */

import { useEffect, useState } from 'react'
import useToastStore, { Toast } from '../../store/toastStore'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface ToastContainerProps {
  /** Maximum visible toasts (default: 3) */
  maxVisible?: number
}

// ─── Toast type colors ────────────────────────────────────────────────────────

const TOAST_TYPE_STYLES: Record<Toast['type'], string> = {
  info: 'bg-slate-800 border-slate-600',
  success: 'bg-slate-800 border-green-600',
  warning: 'bg-slate-800 border-amber-600',
}

// ─── Individual Toast Item ────────────────────────────────────────────────────

interface ToastItemProps {
  toast: Toast
  onDismiss: (id: string) => void
}

function ToastItem({ toast, onDismiss }: ToastItemProps) {
  const [isVisible, setIsVisible] = useState(false)

  useEffect(() => {
    // Trigger enter animation on mount
    const frame = requestAnimationFrame(() => {
      setIsVisible(true)
    })
    return () => cancelAnimationFrame(frame)
  }, [])

  return (
    <div
      className={`flex items-center gap-2 rounded-lg border px-3 py-2 shadow-lg transition-all duration-300 ease-in-out ${
        TOAST_TYPE_STYLES[toast.type]
      } ${
        isVisible
          ? 'translate-y-0 opacity-100'
          : 'translate-y-2 opacity-0'
      }`}
      data-testid={`toast-item-${toast.id}`}
      role="status"
    >
      <span className="text-base" aria-hidden="true">
        {toast.icon}
      </span>
      <span className="text-sm text-slate-200">{toast.message}</span>
      <button
        className="ml-auto text-slate-400 hover:text-slate-200 transition-colors"
        onClick={() => onDismiss(toast.id)}
        aria-label="Dismiss notification"
        data-testid={`toast-dismiss-${toast.id}`}
      >
        <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
          close
        </span>
      </button>
    </div>
  )
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * ToastContainer
 *
 * Renders active toasts from the toast store with enter/exit animations.
 * Positioned in the bottom-left corner of the video area, above the BottomBar.
 * Includes aria-live="polite" for screen reader announcements.
 *
 * Requirements: 5.4, 5.5, 5.6, 5.7
 */
export default function ToastContainer({ maxVisible = 3 }: ToastContainerProps) {
  const toasts = useToastStore((state) => state.toasts)
  const removeToast = useToastStore((state) => state.removeToast)

  // Limit visible toasts to maxVisible
  const visibleToasts = toasts.slice(-maxVisible)

  return (
    <div
      className="absolute bottom-16 left-4 z-50 flex flex-col gap-2 max-w-sm"
      data-testid="toast-container"
    >
      {/* Visible toast items */}
      {visibleToasts.map((toast) => (
        <ToastItem key={toast.id} toast={toast} onDismiss={removeToast} />
      ))}

      {/* Screen reader announcements */}
      <div
        aria-live="polite"
        aria-atomic="true"
        className="sr-only"
        data-testid="toast-aria-live"
      >
        {visibleToasts.length > 0
          ? visibleToasts[visibleToasts.length - 1].message
          : ''}
      </div>
    </div>
  )
}
