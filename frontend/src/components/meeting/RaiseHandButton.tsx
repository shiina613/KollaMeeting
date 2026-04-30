/**
 * RaiseHandButton — allows a Participant to raise or lower their hand
 * to request speaking permission in Meeting_Mode.
 *
 * Visibility rules (Req 22.1):
 * - Only shown when the meeting is in MEETING_MODE
 * - Hidden when the current user already holds Speaking_Permission
 * - Shows "Lower hand" state when the user has a pending raise-hand request
 *
 * Requirements: 22.1, 22.2, 22.7
 */

import { useState, useCallback } from 'react'
import useMeetingStore from '../../store/meetingStore'
import { raiseHand, lowerHand } from '../../services/meetingService'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface RaiseHandButtonProps {
  meetingId: number
  /** ID of the current user — used to check if they hold speaking permission */
  currentUserId: number
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * RaiseHandButton
 *
 * Shown to Participants in MEETING_MODE who do not currently hold
 * Speaking_Permission. Toggles between "Raise Hand" and "Lower Hand" states.
 *
 * Requirements: 22.1, 22.2, 22.7
 */
export default function RaiseHandButton({ meetingId, currentUserId }: RaiseHandButtonProps) {
  const mode = useMeetingStore((s) => s.mode)
  const speakingPermission = useMeetingStore((s) => s.speakingPermission)
  const raiseHandRequests = useMeetingStore((s) => s.raiseHandRequests)

  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // ── Derived state ──────────────────────────────────────────────────────────

  const isMeetingMode = mode === 'MEETING_MODE'
  const isSpeaker = speakingPermission?.userId === currentUserId
  const hasRaisedHand = raiseHandRequests.some((r) => r.userId === currentUserId)

  // ── Visibility: hidden outside MEETING_MODE or when holding permission ─────

  if (!isMeetingMode || isSpeaker) return null

  // ── Handlers ───────────────────────────────────────────────────────────────

  const handleToggle = useCallback(async () => {
    if (isLoading) return

    setIsLoading(true)
    setError(null)

    try {
      if (hasRaisedHand) {
        await lowerHand(meetingId)
        // The WebSocket RAISE_HAND_CANCELLED event will update the store
      } else {
        await raiseHand(meetingId)
        // The WebSocket RAISE_HAND event will update the store
      }
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        (hasRaisedHand
          ? 'Không thể hạ tay. Vui lòng thử lại.'
          : 'Không thể xin phát biểu. Vui lòng thử lại.')
      setError(message)
    } finally {
      setIsLoading(false)
    }
  }, [meetingId, hasRaisedHand, isLoading])

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      className="flex flex-col items-center gap-1"
      data-testid="raise-hand-button-container"
    >
      <button
        onClick={handleToggle}
        disabled={isLoading}
        className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg text-body-sm font-semibold
          transition-colors disabled:opacity-60 disabled:cursor-not-allowed
          ${hasRaisedHand
            ? 'bg-amber-500 text-white hover:bg-amber-600 ring-2 ring-amber-300 ring-offset-1'
            : 'bg-surface-container text-on-surface hover:bg-surface-container-high border border-outline'
          }`}
        aria-label={hasRaisedHand ? 'Hạ tay' : 'Xin phát biểu'}
        aria-pressed={hasRaisedHand}
        data-testid="raise-hand-button"
      >
        {isLoading ? (
          <div
            className={`w-4 h-4 border-2 rounded-full animate-spin
              ${hasRaisedHand ? 'border-white border-t-transparent' : 'border-on-surface border-t-transparent'}`}
          />
        ) : (
          <span
            className="material-symbols-outlined text-[18px]"
            aria-hidden="true"
            style={hasRaisedHand ? { fontVariationSettings: "'FILL' 1" } : undefined}
          >
            pan_tool
          </span>
        )}
        {hasRaisedHand ? 'Hạ tay' : 'Xin phát biểu'}
      </button>

      {/* Error message */}
      {error && (
        <p
          className="text-label-md text-error text-center max-w-[160px]"
          role="alert"
          data-testid="raise-hand-error"
        >
          {error}
        </p>
      )}

      {/* Status hint */}
      {hasRaisedHand && !error && (
        <p
          className="text-label-md text-amber-600 text-center"
          aria-live="polite"
          data-testid="raise-hand-status"
        >
          Đang chờ được cấp quyền...
        </p>
      )}
    </div>
  )
}
