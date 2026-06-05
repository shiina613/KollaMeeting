/**
 * BottomBar — unified bottom bar below the video area for all participant roles.
 *
 * Displays:
 * - RaiseHandButton (for non-host participants in MEETING_MODE)
 * - MeetingTimer showing elapsed time since meeting start
 *
 * Responsive behavior:
 * - < 768px: compact spacing with icon-only buttons and tooltips
 * - ≥ 768px: full controls with labels
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */

import RaiseHandButton from './RaiseHandButton'
import MeetingTimer from './MeetingTimer'
import type { Meeting } from '../../types/meeting'
import useMeetingStore from '../../store/meetingStore'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface BottomBarProps {
  meeting: Meeting
  isHost: boolean
  isSecretary: boolean
  currentUserId?: number
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * BottomBar
 *
 * Renders below the video area for all participant roles.
 * Shows RaiseHandButton when user is non-host in MEETING_MODE.
 * Always displays MeetingTimer showing elapsed time.
 * Uses dark background (slate-800) matching the video area.
 * Compact mode with icon-only buttons on < 768px.
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */
export default function BottomBar({
  meeting,
  isHost,
  isSecretary: _isSecretary,
  currentUserId,
}: BottomBarProps) {
  const mode = useMeetingStore((s) => s.mode)

  const isMeetingMode = mode === 'MEETING_MODE'
  const showRaiseHand = !isHost && isMeetingMode && currentUserId != null

  return (
    <div
      className="flex items-center justify-between px-4 py-2 md:px-6 md:py-3 bg-slate-800 border-t border-slate-700 shrink-0"
      data-testid="bottom-bar"
      aria-label="Thanh hành động cuộc họp"
    >
      {/* Left: Raise Hand (non-host in MEETING_MODE) */}
      <div className="flex items-center gap-2" data-testid="bottom-bar-left">
        {showRaiseHand && (
          <div className="[&_button]:md:px-4 [&_button]:px-2 [&_button]:md:gap-2 [&_button]:gap-1" data-testid="bottom-bar-raise-hand">
            <RaiseHandButton
              meetingId={meeting.id}
              currentUserId={currentUserId}
            />
          </div>
        )}
      </div>

      {/* Right: Meeting Timer */}
      <div className="flex items-center gap-2" data-testid="bottom-bar-right">
        <MeetingTimer joinedAt={meeting.startTime} />
      </div>
    </div>
  )
}
