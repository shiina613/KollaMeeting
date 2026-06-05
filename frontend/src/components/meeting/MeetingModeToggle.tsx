/**
 * MeetingModeToggle — hiển thị chế độ cuộc họp (FREE_MODE / MEETING_MODE).
 *
 * - Mọi thành viên: badge + mô tả (chỉ đọc).
 * - Chủ trì / thư ký: thêm công tắc chuyển chế độ, chỉ sau khi Jitsi đã join (`conferenceReady`).
 *
 * When switching to MEETING_MODE → WS MODE_CHANGED triggers muteAll (moderators) or muteLocal
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
  /** Chủ trì hoặc thư ký — được thấy và dùng công tắc khi `conferenceReady` */
  canControlMode: boolean
  /** Jitsi đã `videoConferenceJoined` — bắt buộc để hiện và bấm công tắc */
  conferenceReady: boolean
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MeetingModeToggle
 *
 * Requirements: 21.4, 21.5, 21.6
 */
export default function MeetingModeToggle({
  meetingId,
  onModeChanged,
  canControlMode,
  conferenceReady,
}: MeetingModeToggleProps) {
  const { user } = useAuthStore()
  const mode = useMeetingStore((s) => s.mode)
  const [isSwitching, setIsSwitching] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const isMeetingMode = mode === 'MEETING_MODE'
  const showModeSwitch = canControlMode && conferenceReady

  const handleToggle = useCallback(async () => {
    if (!showModeSwitch || isSwitching) return

    const targetMode: MeetingMode = isMeetingMode ? 'FREE_MODE' : 'MEETING_MODE'

    setIsSwitching(true)
    setError(null)

    try {
      await api.post<ApiResponse<Meeting>>(`/meetings/${meetingId}/mode`, {
        mode: targetMode,
      })
      onModeChanged?.(targetMode)
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Không thể chuyển chế độ họp. Vui lòng thử lại.'
      setError(message)
    } finally {
      setIsSwitching(false)
    }
  }, [showModeSwitch, isSwitching, isMeetingMode, meetingId, onModeChanged])

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      className="flex flex-col gap-1"
      data-testid="meeting-mode-toggle"
      aria-label="Chế độ cuộc họp"
    >
      <div className="flex items-center gap-3 flex-wrap">
        {/* Icon-only badge on < 768px (muted outline style per requirement 9.4) */}
        <span
          className={`inline-flex md:hidden items-center gap-1.5 px-2 py-1 rounded-full text-label-md font-semibold border
            ${isMeetingMode
              ? 'border-amber-500/50 text-amber-400'
              : 'border-green-500/50 text-green-400'
            }`}
          data-testid="mode-badge-icon"
          aria-live="polite"
          aria-label={isMeetingMode ? 'Chế độ họp' : 'Chế độ tự do'}
          title={isMeetingMode ? 'Chế độ họp' : 'Chế độ tự do'}
        >
          <span
            className="material-symbols-outlined text-[14px]"
            aria-hidden="true"
          >
            {isMeetingMode ? 'record_voice_over' : 'groups'}
          </span>
        </span>

        {/* Full badge with text on >= 768px */}
        <span
          className={`hidden md:inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-label-md font-semibold
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

        {/* Switch control — hidden on < 768px per requirement 2.3 */}
        {showModeSwitch && (
          <div className="hidden md:flex items-center gap-2 shrink-0">
            <span
              className={`text-label-md font-medium tabular-nums transition-colors
                ${isMeetingMode ? 'text-slate-500' : 'text-white'}`}
              aria-hidden="true"
            >
              Tự do
            </span>

            <button
              type="button"
              role="switch"
              aria-checked={isMeetingMode}
              aria-label={isMeetingMode ? 'Chuyển sang chế độ tự do' : 'Chuyển sang chế độ họp'}
              disabled={isSwitching}
              onClick={handleToggle}
              data-testid="mode-toggle-button"
              className={`group relative h-7 w-12 shrink-0 rounded-full transition-colors
                focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-400
                focus-visible:ring-offset-2 focus-visible:ring-offset-slate-800
                disabled:opacity-55 disabled:cursor-not-allowed
                ${isMeetingMode ? 'bg-amber-500 hover:bg-amber-400' : 'bg-slate-600 hover:bg-slate-500'}
              `}
            >
              <span
                className={`pointer-events-none absolute top-1 left-1 flex h-5 w-5 items-center justify-center
                  rounded-full bg-white shadow-md ring-1 ring-black/5 transition-transform duration-200 ease-out
                  ${isMeetingMode ? 'translate-x-5' : 'translate-x-0'}
                `}
                aria-hidden="true"
              >
                {isSwitching && (
                  <span
                    className="h-2.5 w-2.5 border-2 border-slate-500 border-t-transparent rounded-full animate-spin"
                    aria-hidden="true"
                  />
                )}
              </span>
            </button>

            <span
              className={`text-label-md font-medium tabular-nums transition-colors
                ${isMeetingMode ? 'text-white' : 'text-slate-500'}`}
              aria-hidden="true"
            >
              Họp
            </span>
          </div>
        )}
      </div>

      {error && (
        <p className="text-label-md text-error" role="alert" data-testid="mode-toggle-error">
          {error}
        </p>
      )}

      {/* Description text — hidden on < 768px to save space */}
      <p className="hidden md:block text-label-md text-slate-400 max-w-md" aria-live="polite">
        {isMeetingMode
          ? 'Chỉ người được cấp quyền mới có thể phát biểu'
          : 'Tất cả thành viên có thể bật mic đồng thời'
        }
      </p>

      <span className="sr-only">
        {user?.id && (
          canControlMode
            ? conferenceReady
              ? 'Bạn có thể chuyển chế độ cuộc họp bằng công tắc.'
              : 'Đang kết nối phòng — công tắt chế độ sẽ hiện sau khi vào phòng xong.'
            : 'Bạn xem chế độ hiện tại; chỉ chủ trì hoặc thư ký mới đổi được chế độ.'
        )}
      </span>
    </div>
  )
}
