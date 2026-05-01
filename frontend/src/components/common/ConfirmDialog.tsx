/**
 * ConfirmDialog — reusable confirmation modal.
 * Requirements: 1.4
 */

interface ConfirmDialogProps {
  /** Dialog title */
  title: string
  /** Body message */
  message: string
  /** Label for the confirm button (default: "Xác nhận") */
  confirmLabel?: string
  /** Label for the cancel button (default: "Hủy") */
  cancelLabel?: string
  /** Whether the confirm action is destructive (renders button in error color) */
  destructive?: boolean
  /** Whether the confirm action is in progress */
  loading?: boolean
  onConfirm: () => void
  onClose: () => void
}

/**
 * Generic confirmation dialog with accessible markup.
 * Renders a modal overlay with title, message, cancel and confirm buttons.
 */
export default function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Xác nhận',
  cancelLabel = 'Hủy',
  destructive = false,
  loading = false,
  onConfirm,
  onClose,
}: ConfirmDialogProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
      data-testid="confirm-dialog"
    >
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-sm shadow-xl">
        <h2
          id="confirm-dialog-title"
          className="text-h3 font-semibold text-on-surface mb-2"
          data-testid="confirm-dialog-title"
        >
          {title}
        </h2>
        <p
          className="text-body-sm text-on-surface-variant mb-6"
          data-testid="confirm-dialog-message"
        >
          {message}
        </p>
        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            data-testid="confirm-dialog-cancel"
            className="px-4 py-2 rounded-xl text-button font-medium text-on-surface
                       border border-outline-variant hover:bg-surface-container
                       disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            {cancelLabel}
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={loading}
            data-testid="confirm-dialog-confirm"
            className={`px-4 py-2 rounded-xl text-button font-medium text-white
                        disabled:opacity-60 disabled:cursor-not-allowed transition-colors
                        flex items-center gap-2
                        ${destructive
                          ? 'bg-error hover:bg-error/90'
                          : 'bg-primary hover:bg-primary/90'
                        }`}
          >
            {loading && (
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
            )}
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
