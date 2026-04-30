/**
 * MinutesEditor — rich text editor for Secretary to edit meeting minutes.
 *
 * - Uses a <textarea> for HTML content editing (TipTap not installed)
 * - Only visible/enabled for SECRETARY role users
 * - Submits contentHtml to PUT /api/v1/meetings/{id}/minutes/edit
 * - Shows loading state during submission
 * - Shows success/error feedback
 *
 * Requirements: 25.5
 */

import { useState, useCallback } from 'react'
import { editMinutes } from '../../services/minutesService'
import useAuthStore from '../../store/authStore'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MinutesEditorProps {
  meetingId: number
  initialContent?: string
  onSuccess?: () => void
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MinutesEditor
 *
 * Allows the Secretary to edit the minutes HTML content and submit it.
 * Renders nothing if the current user is not a SECRETARY or ADMIN.
 *
 * Requirements: 25.5
 */
export default function MinutesEditor({
  meetingId,
  initialContent = '',
  onSuccess,
}: MinutesEditorProps) {
  const { user } = useAuthStore()
  const [content, setContent] = useState(initialContent)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // Only SECRETARY (and ADMIN) can edit minutes
  const canEdit = user?.role === 'SECRETARY' || user?.role === 'ADMIN'

  const handleSubmit = useCallback(
    async (e: React.FormEvent) => {
      e.preventDefault()

      if (!canEdit || isSubmitting) return

      setIsSubmitting(true)
      setSuccessMessage(null)
      setErrorMessage(null)

      try {
        await editMinutes(meetingId, content)
        setSuccessMessage('Biên bản đã được lưu thành công.')
        onSuccess?.()
      } catch (err) {
        const message =
          (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
          'Không thể lưu biên bản. Vui lòng thử lại.'
        setErrorMessage(message)
      } finally {
        setIsSubmitting(false)
      }
    },
    [canEdit, isSubmitting, meetingId, content, onSuccess],
  )

  // ── Not authorized ─────────────────────────────────────────────────────────

  if (!canEdit) {
    return null
  }

  // ── Editor form ────────────────────────────────────────────────────────────

  return (
    <form
      onSubmit={handleSubmit}
      className="flex flex-col gap-4"
      data-testid="minutes-editor"
      aria-label="Chỉnh sửa biên bản cuộc họp"
    >
      {/* Label */}
      <div>
        <label
          htmlFor="minutes-content"
          className="block text-label-lg font-medium text-on-surface mb-1"
        >
          Nội dung biên bản
        </label>
        <p className="text-body-sm text-on-surface-variant">
          Nhập nội dung HTML cho biên bản cuộc họp. Nội dung sẽ được chuyển đổi thành PDF.
        </p>
      </div>

      {/* Textarea */}
      <textarea
        id="minutes-content"
        value={content}
        onChange={(e) => setContent(e.target.value)}
        disabled={isSubmitting}
        rows={20}
        className="w-full rounded-lg border border-outline bg-surface px-3 py-2
          text-body-md text-on-surface font-mono
          focus:outline-none focus:ring-2 focus:ring-primary focus:border-primary
          disabled:opacity-60 disabled:cursor-not-allowed
          resize-y"
        placeholder="<h1>Biên bản cuộc họp</h1>&#10;<p>Nội dung biên bản...</p>"
        aria-label="Nội dung HTML biên bản"
        data-testid="minutes-content-textarea"
      />

      {/* Success message */}
      {successMessage && (
        <p
          className="text-body-md text-success bg-success/10 rounded-lg px-3 py-2"
          role="status"
          data-testid="minutes-editor-success"
        >
          {successMessage}
        </p>
      )}

      {/* Error message */}
      {errorMessage && (
        <p
          className="text-body-md text-error bg-error/10 rounded-lg px-3 py-2"
          role="alert"
          data-testid="minutes-editor-error"
        >
          {errorMessage}
        </p>
      )}

      {/* Submit button */}
      <div className="flex justify-end">
        <button
          type="submit"
          disabled={isSubmitting || !content.trim()}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg
            bg-primary text-on-primary text-label-lg font-medium
            hover:bg-primary/90 transition-colors
            disabled:opacity-60 disabled:cursor-not-allowed"
          aria-label="Lưu biên bản"
          data-testid="minutes-editor-submit"
        >
          {isSubmitting ? (
            <>
              <div
                className="w-4 h-4 border-2 border-on-primary border-t-transparent rounded-full animate-spin"
                aria-hidden="true"
              />
              Đang lưu...
            </>
          ) : (
            <>
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                save
              </span>
              Lưu biên bản
            </>
          )}
        </button>
      </div>
    </form>
  )
}
