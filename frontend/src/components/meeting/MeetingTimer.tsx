/**
 * MeetingTimer — displays elapsed meeting time with a clock icon.
 *
 * Uses the `useMeetingTimer` hook internally to calculate and format
 * elapsed time since the user joined. Renders with a monospace font
 * to prevent layout shifts during updates.
 *
 * Requirements: 4.1, 4.2, 4.5
 */

import { useMeetingTimer } from '../../hooks/useMeetingTimer'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MeetingTimerProps {
  /** ISO timestamp of when the user joined */
  joinedAt: string
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MeetingTimer
 *
 * Displays elapsed meeting time in MM:SS or HH:MM:SS format with a clock icon.
 * Uses monospace font to prevent layout shifts as digits change.
 *
 * Requirements: 4.1, 4.2, 4.5
 */
export default function MeetingTimer({ joinedAt }: MeetingTimerProps) {
  const { formatted } = useMeetingTimer(joinedAt)

  return (
    <div
      className="inline-flex items-center gap-1.5 text-slate-300"
      data-testid="meeting-timer"
      aria-label={`Thời gian họp: ${formatted}`}
      aria-live="off"
    >
      <span
        className="material-symbols-outlined text-[18px]"
        aria-hidden="true"
      >
        schedule
      </span>
      <span className="font-mono text-body-sm" data-testid="meeting-timer-value">
        {formatted}
      </span>
    </div>
  )
}
