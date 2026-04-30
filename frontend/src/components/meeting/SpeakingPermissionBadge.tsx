/**
 * SpeakingPermissionBadge — displays who currently holds speaking permission
 * in Meeting_Mode.
 *
 * - Reads speakingPermission from meetingStore (driven by WebSocket events)
 * - Shows the speaker's name with a pulsing mic icon
 * - Shows "Không có ai đang phát biểu" when no one holds permission
 * - Only meaningful in MEETING_MODE; renders null in FREE_MODE
 *
 * Requirements: 22.5
 */

import useMeetingStore from '../../store/meetingStore'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface SpeakingPermissionBadgeProps {
  /** ID of the current user — used to show "bạn" label */
  currentUserId?: number
  /** Additional CSS class for the container */
  className?: string
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * SpeakingPermissionBadge
 *
 * Displays the current speaker in Meeting_Mode.
 * Renders nothing when the meeting is in FREE_MODE.
 *
 * Requirements: 22.5
 */
export default function SpeakingPermissionBadge({
  currentUserId,
  className = '',
}: SpeakingPermissionBadgeProps) {
  const mode = useMeetingStore((s) => s.mode)
  const speakingPermission = useMeetingStore((s) => s.speakingPermission)

  // Only relevant in MEETING_MODE
  if (mode !== 'MEETING_MODE') return null

  const isSelf = speakingPermission?.userId === currentUserId

  return (
    <div
      className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full
        ${speakingPermission
          ? 'bg-green-100 border border-green-300'
          : 'bg-surface-container border border-outline-variant'
        } ${className}`}
      data-testid="speaking-permission-badge"
      aria-live="polite"
      aria-label={
        speakingPermission
          ? `${speakingPermission.userName}${isSelf ? ' (bạn)' : ''} đang phát biểu`
          : 'Không có ai đang phát biểu'
      }
    >
      {speakingPermission ? (
        <>
          {/* Pulsing mic icon */}
          <span
            className="relative flex h-4 w-4 shrink-0"
            aria-hidden="true"
          >
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75" />
            <span className="material-symbols-outlined text-[16px] text-green-600 relative"
              style={{ fontVariationSettings: "'FILL' 1" }}>
              mic
            </span>
          </span>

          {/* Speaker name */}
          <span className="text-body-sm font-semibold text-green-800 truncate max-w-[140px]">
            {speakingPermission.userName}
            {isSelf && (
              <span className="text-green-600 font-normal ml-1">(bạn)</span>
            )}
          </span>
        </>
      ) : (
        <>
          {/* Silent mic icon */}
          <span
            className="material-symbols-outlined text-[16px] text-on-surface-variant"
            aria-hidden="true"
          >
            mic_off
          </span>
          <span className="text-body-sm text-on-surface-variant">
            Chưa có ai phát biểu
          </span>
        </>
      )}
    </div>
  )
}
