/**
 * TopBar — responsive top bar for the MeetingRoom page.
 *
 * Displays meeting title (truncated), meeting code, MeetingModeToggle,
 * SpeakingPermissionBadge, REC indicator, sidebar toggle, and
 * ConnectionQualityIndicator.
 *
 * Responsive layout:
 * - < 640px: two-row layout (badges/REC move to second row)
 * - 640px–767px: single row, truncated title, no meeting code
 * - 768px+: full layout with all controls visible
 *
 * Badge hierarchy:
 * - Mode badge: muted (outline-only with icon)
 * - Speaking badge: prominent green when active
 * - REC indicator: subtle pulsing dot
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 9.1, 9.2, 9.3, 9.4, 9.5
 */

import MeetingModeToggle from './MeetingModeToggle'
import SpeakingPermissionBadge from './SpeakingPermissionBadge'
import ConnectionQualityIndicator from './ConnectionQualityIndicator'
import type { Meeting, MeetingMode } from '../../types/meeting'
import type { ConnectionStats } from '../../utils/connectionQuality'
import useAuthStore from '../../store/authStore'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface TopBarProps {
  meeting: Meeting
  isHost: boolean
  isSecretary: boolean
  conferenceReady: boolean
  isCapturing: boolean
  isSidebarOpen: boolean
  onToggleSidebar: () => void
  onModeChanged: (mode: MeetingMode) => void
  /** Connection quality stats for the indicator */
  connectionStats: ConnectionStats | null
  /** Ref for the sidebar toggle button (focus return target) */
  sidebarToggleRef?: React.RefObject<HTMLButtonElement>
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * TopBar
 *
 * Renders the top bar of the meeting room with responsive layout.
 * Two-row on < 640px, single row with truncation on sm, full on md+.
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 9.1, 9.2, 9.3, 9.4, 9.5
 */
export default function TopBar({
  meeting,
  isHost,
  isSecretary,
  conferenceReady,
  isCapturing,
  isSidebarOpen,
  onToggleSidebar,
  onModeChanged,
  connectionStats,
  sidebarToggleRef,
}: TopBarProps) {
  const { user } = useAuthStore()
  const canControlMeetingMode = isHost || isSecretary

  return (
    <div
      className="flex flex-wrap items-center justify-between gap-2 px-4 py-2 bg-slate-800 border-b border-slate-700 shrink-0"
      data-testid="top-bar"
      aria-label="Thanh điều khiển cuộc họp"
    >
      {/* ── Row 1: Title + primary controls ── */}
      <div className="flex items-center gap-3 min-w-0">
        {/* Meeting title — truncated responsively */}
        <h1
          className="text-body-sm font-semibold text-white truncate max-w-[120px] md:max-w-xs"
          title={meeting.title}
          data-testid="top-bar-title"
        >
          {meeting.title}
        </h1>

        {/* Meeting code — hidden on < 768px */}
        <span
          className="hidden md:inline text-label-md text-slate-400 font-mono"
          data-testid="top-bar-meeting-code"
        >
          {meeting.meetingCode}
        </span>
      </div>

      {/* ── Row 1 right: Controls always visible ── */}
      <div className="flex items-center gap-2">
        {/* MeetingModeToggle — icon-only on < 768px via internal responsive */}
        <div className="shrink-0" data-testid="top-bar-mode-toggle">
          <MeetingModeToggle
            meetingId={meeting.id}
            canControlMode={canControlMeetingMode}
            conferenceReady={conferenceReady}
            onModeChanged={onModeChanged}
          />
        </div>

        {/* SpeakingPermissionBadge — prominent when active, hidden on < 640px (moves to row 2) */}
        <div className="hidden sm:block shrink-0" data-testid="top-bar-speaking-badge-desktop">
          <SpeakingPermissionBadge currentUserId={user?.id} />
        </div>

        {/* REC indicator — hidden on < 640px (moves to row 2) */}
        {isCapturing && (
          <div
            className="hidden sm:inline-flex items-center gap-1.5 text-label-md font-medium text-red-400"
            aria-label="Đang ghi âm và phiên âm"
            data-testid="top-bar-rec-indicator"
          >
            <span
              className="w-2 h-2 rounded-full bg-red-500 animate-pulse"
              aria-hidden="true"
            />
            <span className="text-red-400">REC</span>
          </div>
        )}

        {/* Connection quality indicator */}
        <div className="shrink-0" data-testid="top-bar-connection-quality">
          <ConnectionQualityIndicator stats={connectionStats} />
        </div>

        {/* Sidebar toggle */}
        <button
          ref={sidebarToggleRef}
          onClick={onToggleSidebar}
          className="p-1.5 rounded-md text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
          aria-label={isSidebarOpen ? 'Ẩn thanh bên' : 'Hiện thanh bên'}
          data-testid="sidebar-toggle"
        >
          <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
            {isSidebarOpen ? 'right_panel_close' : 'right_panel_open'}
          </span>
        </button>
      </div>

      {/* ── Row 2: Badges on < 640px (two-row layout) ── */}
      <div
        className="flex sm:hidden items-center gap-2 w-full"
        data-testid="top-bar-row-2"
      >
        {/* SpeakingPermissionBadge — shown in row 2 on mobile */}
        <SpeakingPermissionBadge currentUserId={user?.id} />

        {/* REC indicator — shown in row 2 on mobile */}
        {isCapturing && (
          <div
            className="inline-flex items-center gap-1.5 text-label-md font-medium text-red-400"
            aria-label="Đang ghi âm và phiên âm"
            data-testid="top-bar-rec-indicator-mobile"
          >
            <span
              className="w-2 h-2 rounded-full bg-red-500 animate-pulse"
              aria-hidden="true"
            />
            <span className="text-red-400">REC</span>
          </div>
        )}
      </div>
    </div>
  )
}
