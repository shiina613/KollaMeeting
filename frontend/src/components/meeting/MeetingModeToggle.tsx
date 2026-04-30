/**
 * MeetingModeToggle — displays the current meeting mode and allows the Host
 * to switch between FREE_MODE and MEETING_MODE.
 *
 * - FREE_MODE: all participants can use their mic simultaneously
 * - MEETING_MODE: only the participant with Speaking_Permission can speak
 *
 * When switching to MEETING_MODE → muteAll via Jitsi API
 * When switching to FREE_MODE → restore mic control (no muteAll)
 *
 * Requirements: 21.4, 21.5, 21.6
 */

import { useState, useCallback } from 'react'
import api from '../../services/api'
import useAuthStore from '../../store/authStore'
import useMeetingStore from '../../store/meetingStore'
import type { ApiResponse } from '../../types/api'
import type { Meeting, MeetingMode } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MeetingModeToggleProps {
  meetingId: number
  /** Called after a successful mode switch so the parent can trigger Jitsi actions */
  onModeChanged?: (newMode: MeetingMode) => void
  /** Whether the current user is the Host of this meeting */
  isHost: boolean
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MeetingModeToggle
 *
 * Visible to all participants (shows current mode).
 * Only the Host can click the toggle button.
 *
 * Requirements: 21.4, 21.5, 21.6
 */
export default function MeetingModeToggle({
  meetingId,
  onModeChanged,
  isHost,
}: MeetingModeToggleProps) {
  const { user } = useAuthStore()
  const mode = useMeetingStore((s) => s.mode)
  const [isSwitching, setIsSwitching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isMeetingMode = mode === 'MEETING_MODE'

  const handleToggle = useCallback(async () => {
    if (!isHost || isSwitching) return

    const targetMode: MeetingMode = isMeetingMode ? 'FREE_MODE' : 'MEETING_MODE'

    setIsSwitching(true)
    setError(null)

    try {
      await api.post<ApiResponse<Meeting>>(`/meetings/${meetingId}/mode`, {
        mode: targetMode,
      })
      // The WebSocket MODE_CHANGED event will update the store.
      // We also call onModeChanged immediately so the parent can react
      // (e.g. muteAll via Jitsi) without waiting for the WS event.
      onModeChanged?.(targetMode)
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Không thể chuyển chế độ họp. Vui lòng thử lại.'
      setError(message)
    } finally {
      setIsSwitching(false)
    }
  }, [isHost, isSwitching, isMeetingMode, meetingId, onModeChanged])

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      className="flex flex-col gap-1"
      data-testid="meeting-mode-toggle"
      aria-label="Chế độ cuộc họp"
    >
      {/* Mode indicator — visible to all */}
      <div className="flex items-center gap-2">
        <span
          className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-label-md font-semibold
            ${isMeetingMode
              ? 'bg-amber-100 text-amber-700'
              : 'bg-green-100 text-green-700'
            }`}
          data-testid="mode-badge"
          aria-live="polite"
        >
          <span
            className="material-symbols-outlined text-[14px]"
            aria-hidden="true"
          >
            {isMeetingMode ? 'record_voice_over' : 'groups'}
          </span>
          {isMeetingMode ? 'Chế độ họp' : 'Chế độ tự do'}
        </span>

        {/* Toggle button — only for Host */}
        {isHost && (
          <button
            onClick={handleToggle}
            disabled={isSwitching}
            className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-label-md font-medium
              transition-colors disabled:opacity-60 disabled:cursor-not-allowed
              ${isMeetingMode
                ? 'bg-green-600 text-white hover:bg-green-700'
                : 'bg-amber-500 text-white hover:bg-amber-600'
              }`}
            aria-label={isMeetingMode ? 'Chuyển sang chế độ tự do' : 'Chuyển sang chế độ họp'}
            data-testid="mode-toggle-button"
          >
            {isSwitching ? (
              <div className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
            ) : (
              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">
                swap_horiz
              </span>
            )}
            {isMeetingMode ? 'Chuyển tự do' : 'Chuyển họp'}
          </button>
        )}
      </div>

      {/* Error message */}
      {error && (
        <p className="text-label-md text-error" role="alert" data-testid="mode-toggle-error">
          {error}
        </p>
      )}

      {/* Mode description — helps participants understand the current mode */}
      <p className="text-label-md text-on-surface-variant" aria-live="polite">
        {isMeetingMode
          ? 'Chỉ người được cấp quyền mới có thể phát biểu'
          : 'Tất cả thành viên có thể bật mic đồng thời'
        }
      </p>

      {/* Hidden info for screen readers */}
      <span className="sr-only">
        {user?.id && `Bạn ${isHost ? 'là' : 'không phải'} chủ trì cuộc họp này.`}
      </span>
    </div>
  )
}
