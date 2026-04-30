/**
 * TranscriptionPriorityControl — allows ADMIN/SECRETARY to toggle the
 * transcription priority of an active meeting between HIGH_PRIORITY and
 * NORMAL_PRIORITY.
 *
 * Also displays a TRANSCRIPTION_UNAVAILABLE indicator when Gipformer is down.
 *
 * Requirements: 8.12
 */

import { useState, useCallback } from 'react'
import { setTranscriptionPriority } from '../../services/meetingService'
import useMeetingStore from '../../store/meetingStore'
import type { TranscriptionPriority } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface TranscriptionPriorityControlProps {
  meetingId: number
  currentPriority: TranscriptionPriority
  /** Whether the current user can change priority (ADMIN or SECRETARY role). */
  canChangePriority: boolean
  /** Called after a successful priority change so the parent can update state. */
  onPriorityChanged?: (priority: TranscriptionPriority) => void
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * TranscriptionPriorityControl
 *
 * Requirements: 8.12
 */
export default function TranscriptionPriorityControl({
  meetingId,
  currentPriority,
  canChangePriority,
  onPriorityChanged,
}: TranscriptionPriorityControlProps) {
  const { isTranscriptionAvailable } = useMeetingStore()
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isHighPriority = currentPriority === 'HIGH_PRIORITY'

  // ── Toggle priority ────────────────────────────────────────────────────────

  const handleToggle = useCallback(async () => {
    if (!canChangePriority || isLoading) return

    const newPriority: TranscriptionPriority = isHighPriority
      ? 'NORMAL_PRIORITY'
      : 'HIGH_PRIORITY'

    setIsLoading(true)
    setError(null)

    try {
      await setTranscriptionPriority(meetingId, newPriority)
      onPriorityChanged?.(newPriority)
    } catch {
      setError('Không thể thay đổi mức ưu tiên phiên âm.')
    } finally {
      setIsLoading(false)
    }
  }, [canChangePriority, isHighPriority, isLoading, meetingId, onPriorityChanged])

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      className="flex flex-col gap-2"
      data-testid="transcription-priority-control"
    >
      {/* Unavailable indicator */}
      {!isTranscriptionAvailable && (
        <div
          className="flex items-center gap-1.5 px-2 py-1 rounded-md bg-error-container text-on-error-container text-label-sm"
          role="status"
          aria-live="polite"
          data-testid="transcription-unavailable-indicator"
        >
          <span className="material-symbols-outlined text-[14px]" aria-hidden="true">
            cloud_off
          </span>
          <span>Phiên âm không khả dụng</span>
        </div>
      )}

      {/* Priority toggle */}
      <div className="flex items-center gap-2">
        {/* Priority badge */}
        <span
          className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-label-sm font-medium
            ${isHighPriority
              ? 'bg-primary-container text-on-primary-container'
              : 'bg-surface-variant text-on-surface-variant'
            }`}
          data-testid="priority-badge"
          aria-label={`Mức ưu tiên phiên âm: ${isHighPriority ? 'Cao' : 'Thường'}`}
        >
          <span className="material-symbols-outlined text-[12px]" aria-hidden="true">
            {isHighPriority ? 'priority_high' : 'low_priority'}
          </span>
          {isHighPriority ? 'Ưu tiên cao' : 'Thường'}
        </span>

        {/* Toggle button — only for ADMIN/SECRETARY */}
        {canChangePriority && (
          <button
            type="button"
            onClick={handleToggle}
            disabled={isLoading}
            className={`flex items-center gap-1 px-2 py-1 rounded-md text-label-sm font-medium
              transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary
              ${isLoading
                ? 'opacity-50 cursor-not-allowed bg-surface-variant text-on-surface-variant'
                : 'bg-secondary-container text-on-secondary-container hover:bg-secondary/20 cursor-pointer'
              }`}
            aria-label={isHighPriority ? 'Chuyển sang ưu tiên thường' : 'Chuyển sang ưu tiên cao'}
            data-testid="priority-toggle-button"
          >
            {isLoading ? (
              <span className="material-symbols-outlined text-[14px] animate-spin" aria-hidden="true">
                progress_activity
              </span>
            ) : (
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">
                swap_vert
              </span>
            )}
            {isHighPriority ? 'Hạ xuống' : 'Nâng lên'}
          </button>
        )}
      </div>

      {/* Error message */}
      {error && (
        <p
          className="text-label-sm text-error"
          role="alert"
          data-testid="priority-error"
        >
          {error}
        </p>
      )}
    </div>
  )
}
