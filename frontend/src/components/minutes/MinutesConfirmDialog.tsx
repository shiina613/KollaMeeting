/**
 * MinutesConfirmDialog — Host confirm dialog with PDF preview before confirming.
 *
 * - Fetches the draft PDF via Axios (JWT-authenticated) and renders it via a
 *   blob URL in an iframe. This bypasses X-Frame-Options: DENY because blob
 *   URLs are same-origin.
 * - Confirm button calls POST /api/v1/meetings/{id}/minutes/confirm (PKCS#7 PDF signature on server)
 * - Shows loading state during confirmation
 * - Only for HOST role users (SECRETARY or ADMIN who is the meeting host)
 *
 * Requirements: 25.4
 */

import { useState, useCallback, useEffect } from 'react'
import { confirmMinutes } from '../../services/minutesService'
import api from '../../services/api'

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
 * with a server-side digital signature (requires signing keystore on backend).
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

  // PDF blob state — fetched via Axios so JWT header is included
  const [pdfBlobUrl, setPdfBlobUrl] = useState<string | null>(null)
  const [pdfLoading, setPdfLoading] = useState(false)
  const [pdfError, setPdfError] = useState(false)

  // Fetch PDF as blob when dialog opens
  useEffect(() => {
    if (!isOpen) return

    let objectUrl: string | null = null

    setPdfLoading(true)
    setPdfError(false)
    setPdfBlobUrl(null)

    api
      .get(`/meetings/${meetingId}/minutes/download`, {
        params: { version: 'draft', inline: true },
        responseType: 'blob',
      })
      .then((response) => {
        const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
        objectUrl = URL.createObjectURL(blob)
        setPdfBlobUrl(objectUrl)
      })
      .catch(() => {
        setPdfError(true)
      })
      .finally(() => {
        setPdfLoading(false)
      })

    // Revoke blob URL when dialog closes or component unmounts
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl)
      setPdfBlobUrl(null)
    }
  }, [isOpen, meetingId])

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
            className="p-1.5 rounded-lg text-on-surface-variant hover:bg-surface-variant transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
            aria-label="Đóng hộp thoại"
            data-testid="confirm-dialog-close"
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
          </button>
        </div>

        {/* Body — PDF preview */}
        <div className="flex-1 overflow-auto px-6 py-4">
          <p className="text-body-md text-on-surface-variant mb-3">
            Vui lòng xem lại biên bản nháp trước khi xác nhận. Sau khi xác nhận, biên bản PDF sẽ được
            ký số bằng chứng thư cấu hình trên máy chủ (có thể kiểm tra chữ ký trong Adobe Reader / Foxit).
          </p>

          <div
            className="w-full rounded-lg overflow-hidden border border-outline-variant"
            data-testid="confirm-dialog-preview"
          >
            {/* Loading */}
            {pdfLoading && (
              <div className="flex flex-col items-center justify-center h-[400px] gap-3 text-on-surface-variant">
                <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
                <p className="text-body-sm">Đang tải biên bản...</p>
              </div>
            )}

            {/* Error */}
            {!pdfLoading && pdfError && (
              <div className="flex flex-col items-center justify-center h-[400px] gap-3 text-on-surface-variant">
                <span className="material-symbols-outlined text-5xl text-error" aria-hidden="true">description</span>
                <p className="text-body-sm text-error">Không thể tải biên bản nháp.</p>
                <p className="text-label-sm text-on-surface-variant text-center">
                  Bạn vẫn có thể xác nhận mà không cần xem trước.
                </p>
              </div>
            )}

            {/* PDF via blob URL — bypasses X-Frame-Options: DENY */}
            {!pdfLoading && !pdfError && pdfBlobUrl && (
              <iframe
                src={pdfBlobUrl}
                title="Xem trước biên bản nháp"
                className="w-full h-[400px] border-0"
                aria-label="Xem trước biên bản nháp"
                data-testid="confirm-dialog-iframe"
              />
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-outline-variant flex flex-col gap-3">
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
            <button
              onClick={handleClose}
              disabled={isConfirming}
              className="px-4 py-2 rounded-lg border border-outline text-label-lg font-medium text-on-surface hover:bg-surface-variant transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              aria-label="Hủy xác nhận"
              data-testid="confirm-dialog-cancel"
            >
              Hủy
            </button>

            <button
              onClick={handleConfirm}
              disabled={isConfirming}
              className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary text-label-lg font-medium hover:bg-primary/90 transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              aria-label="Xác nhận biên bản"
              data-testid="confirm-dialog-confirm"
            >
              {isConfirming ? (
                <>
                  <div className="w-4 h-4 border-2 border-on-primary border-t-transparent rounded-full animate-spin" aria-hidden="true" />
                  Đang xác nhận...
                </>
              ) : (
                <>
                  <span className="material-symbols-outlined text-[18px]" aria-hidden="true">verified</span>
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
