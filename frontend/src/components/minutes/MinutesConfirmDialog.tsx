/**
 * MinutesConfirmDialog — Host confirm dialog with PDF preview before confirming.
 *
 * - Shows the draft PDF in an iframe for preview
 * - Confirm button calls POST /api/v1/meetings/{id}/minutes/confirm
 * - Shows loading state during confirmation
 * - Only for HOST role users (SECRETARY or ADMIN who is the meeting host)
 *
 * Requirements: 25.4
 */

import { useState, useCallback } from 'react'
import { confirmMinutes, getMinutesDownloadUrl } from '../../services/minutesService'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MinutesConfirmDialogProps {
  meetingId: number
  isOpen: boolean
  onClose: () => void
  onSuccess?: () => void
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MinutesConfirmDialog
 *
 * Modal dialog that shows the draft PDF and lets the Host confirm it
 * with a digital stamp.
 *
 * Requirements: 25.4
 */
export default function MinutesConfirmDialog({
  meetingId,
  isOpen,
  onClose,
  onSuccess,
}: MinutesConfirmDialogProps) {
  const [isConfirming, setIsConfirming] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const draftPdfUrl = getMinutesDownloadUrl(meetingId, 'draft')

  const handleConfirm = useCallback(async () => {
    if (isConfirming) return

    setIsConfirming(true)
    setErrorMessage(null)

    try {
      await confirmMinutes(meetingId)
      onSuccess?.()
      onClose()
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Không thể xác nhận biên bản. Vui lòng thử lại.'
      setErrorMessage(message)
    } finally {
      setIsConfirming(false)
    }
  }, [isConfirming, meetingId, onSuccess, onClose])

  const handleClose = useCallback(() => {
    if (isConfirming) return
    setErrorMessage(null)
    onClose()
  }, [isConfirming, onClose])

  // ── Not open ───────────────────────────────────────────────────────────────

  if (!isOpen) return null

  // ── Dialog ─────────────────────────────────────────────────────────────────

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
      role="dialog"
      aria-modal="true"
      aria-labelledby="confirm-dialog-title"
      data-testid="minutes-confirm-dialog"
      onClick={(e) => {
        // Close on backdrop click (not on dialog content)
        if (e.target === e.currentTarget) handleClose()
      }}
    >
      {/* Dialog panel */}
      <div className="relative w-full max-w-3xl mx-4 bg-surface rounded-2xl shadow-xl flex flex-col max-h-[90vh]">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
          <h2
            id="confirm-dialog-title"
            className="text-title-lg font-semibold text-on-surface"
          >
            Xác nhận biên bản cuộc họp
          </h2>
          <button
            onClick={handleClose}
            disabled={isConfirming}
            className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-variant
              transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
            aria-label="Đóng hộp thoại"
            data-testid="confirm-dialog-close"
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
              close
            </span>
          </button>
        </div>

        {/* Body — PDF preview */}
        <div className="flex-1 overflow-auto px-6 py-4">
          <p className="text-body-md text-on-surface-variant mb-3">
            Vui lòng xem lại biên bản nháp trước khi xác nhận. Sau khi xác nhận, biên bản sẽ được
            đóng dấu kỹ thuật số với thông tin của bạn.
          </p>

          <div
            className="w-full rounded-lg overflow-hidden border border-outline-variant"
            data-testid="confirm-dialog-preview"
          >
            <iframe
              src={draftPdfUrl}
              title="Xem trước biên bản nháp"
              className="w-full h-[400px] border-0"
              aria-label="Xem trước biên bản nháp"
              data-testid="confirm-dialog-iframe"
            />
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-outline-variant flex flex-col gap-3">
          {/* Error message */}
          {errorMessage && (
            <p
              className="text-body-md text-error bg-error/10 rounded-lg px-3 py-2"
              role="alert"
              data-testid="confirm-dialog-error"
            >
              {errorMessage}
            </p>
          )}

          <div className="flex justify-end gap-3">
            {/* Cancel button */}
            <button
              onClick={handleClose}
              disabled={isConfirming}
              className="px-4 py-2 rounded-lg border border-outline text-label-lg font-medium
                text-on-surface hover:bg-surface-variant transition-colors
                disabled:opacity-60 disabled:cursor-not-allowed"
              aria-label="Hủy xác nhận"
              data-testid="confirm-dialog-cancel"
            >
              Hủy
            </button>

            {/* Confirm button */}
            <button
              onClick={handleConfirm}
              disabled={isConfirming}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg
                bg-primary text-on-primary text-label-lg font-medium
                hover:bg-primary/90 transition-colors
                disabled:opacity-60 disabled:cursor-not-allowed"
              aria-label="Xác nhận biên bản"
              data-testid="confirm-dialog-confirm"
            >
              {isConfirming ? (
                <>
                  <div
                    className="w-4 h-4 border-2 border-on-primary border-t-transparent rounded-full animate-spin"
                    aria-hidden="true"
                  />
                  Đang xác nhận...
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                    verified
                  </span>
                  Xác nhận biên bản
                </>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}
