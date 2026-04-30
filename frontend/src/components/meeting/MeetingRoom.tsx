/**
 * MeetingRoom — main container for an active meeting session.
 *
 * Combines:
 * - JitsiFrame (video conference)
 * - TranscriptionPanel (real-time transcription, HIGH_PRIORITY only)
 * - RaiseHandPanel (Host view of pending requests)
 * - MeetingModeToggle (FREE_MODE ↔ MEETING_MODE)
 * - ParticipantList (real-time participant list)
 *
 * Responsibilities:
 * - Calls POST /meetings/{id}/join on mount (attendance tracking)
 * - Calls POST /meetings/{id}/leave on unmount
 * - Subscribes to WebSocket meeting events and dispatches to meetingStore
 * - Reacts to mode changes: muteAll on MEETING_MODE, restore on FREE_MODE
 *
 * Requirements: 4.6, 5.1, 5.3
 */

import { useEffect, useRef, useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import JitsiFrame, { type JitsiFrameHandle } from './JitsiFrame'
import TranscriptionPanel from './TranscriptionPanel'
import RaiseHandPanel from './RaiseHandPanel'
import MeetingModeToggle from './MeetingModeToggle'
import ParticipantList from './ParticipantList'
import useWebSocket from '../../hooks/useWebSocket'
import useAuthStore from '../../store/authStore'
import useMeetingStore from '../../store/meetingStore'
import { joinMeeting, leaveMeeting } from '../../services/meetingService'
import type { Meeting, MeetingMode, MeetingEvent } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MeetingRoomProps {
  meeting: Meeting
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MeetingRoom
 *
 * Requirements: 4.6, 5.1, 5.3
 */
export default function MeetingRoom({ meeting }: MeetingRoomProps) {
  const navigate = useNavigate()
  const { user } = useAuthStore()
  const { setActiveMeeting, clearActiveMeeting, handleMeetingEvent, mode } = useMeetingStore()

  const jitsiRef = useRef<JitsiFrameHandle>(null)
  const hasJoinedRef = useRef(false)
  const [sidebarTab, setSidebarTab] = useState<'participants' | 'transcription' | 'raise-hand'>('participants')
  const [joinError, setJoinError] = useState<string | null>(null)

  // ── Derived state ──────────────────────────────────────────────────────────

  const isHost = user?.id === meeting.hostUser?.id
  const isSecretary = user?.id === meeting.secretaryUser?.id
  const isHighPriority = meeting.transcriptionPriority === 'HIGH_PRIORITY'
  const isMeetingMode = mode === 'MEETING_MODE'

  // ── Join / leave ───────────────────────────────────────────────────────────

  useEffect(() => {
    setActiveMeeting(meeting)

    // Notify backend that the user has joined (creates attendance log)
    if (!hasJoinedRef.current) {
      hasJoinedRef.current = true
      joinMeeting(meeting.id).catch((err) => {
        console.error('[MeetingRoom] Failed to notify join:', err)
        setJoinError('Không thể ghi nhận tham gia cuộc họp.')
      })
    }

    return () => {
      // Notify backend that the user has left
      leaveMeeting(meeting.id).catch((err) => {
        console.error('[MeetingRoom] Failed to notify leave:', err)
      })
      clearActiveMeeting()
    }
  }, [meeting.id]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── WebSocket event handler ────────────────────────────────────────────────

  const onMeetingEvent = useCallback(
    (event: MeetingEvent) => {
      // Dispatch to store first
      handleMeetingEvent(event)

      // React to mode changes for Jitsi audio control
      if (event.type === 'MODE_CHANGED') {
        const newMode = (event.payload as { mode: MeetingMode }).mode
        if (newMode === 'MEETING_MODE') {
          // Mute all participants when entering Meeting Mode (Req 21.4)
          jitsiRef.current?.muteAll()
        }
        // FREE_MODE: restore mic control — participants can unmute themselves (Req 21.5)
        // No explicit action needed; Jitsi allows self-unmute by default
      }

      // When meeting ends, navigate back to meeting detail
      if (event.type === 'MEETING_ENDED') {
        navigate(`/meetings/${meeting.id}`, { replace: true })
      }
    },
    [handleMeetingEvent, meeting.id, navigate],
  )

  useWebSocket({
    meetingId: meeting.id,
    onMeetingEvent,
  })

  // ── Mode change handler (from toggle button) ───────────────────────────────

  const handleModeChanged = useCallback((newMode: MeetingMode) => {
    if (newMode === 'MEETING_MODE') {
      jitsiRef.current?.muteAll()
    }
    // FREE_MODE: no action — participants can unmute themselves
  }, [])

  // ── Jitsi event handlers ───────────────────────────────────────────────────

  const handleVideoConferenceLeft = useCallback(() => {
    // User left via Jitsi UI — notify backend and navigate away
    leaveMeeting(meeting.id).catch(() => {})
    clearActiveMeeting()
    navigate(`/meetings/${meeting.id}`, { replace: true })
  }, [meeting.id, clearActiveMeeting, navigate])

  // ── Sidebar tab visibility ─────────────────────────────────────────────────

  const sidebarTabs = [
    { key: 'participants' as const, label: 'Thành viên', icon: 'group' },
    ...(isMeetingMode && isHost
      ? [{ key: 'raise-hand' as const, label: 'Xin phát biểu', icon: 'pan_tool' }]
      : []),
    ...(isHighPriority
      ? [{ key: 'transcription' as const, label: 'Phiên âm', icon: 'subtitles' }]
      : []),
  ]

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      className="flex h-screen bg-slate-900 overflow-hidden"
      data-testid="meeting-room"
      aria-label={`Phòng họp: ${meeting.title}`}
    >
      {/* ── Main area: Jitsi video ── */}
      <div className="flex-1 flex flex-col min-w-0">
        {/* Top bar */}
        <div className="flex items-center justify-between px-4 py-2 bg-slate-800 border-b border-slate-700 shrink-0">
          <div className="flex items-center gap-3">
            <h1 className="text-body-sm font-semibold text-white truncate max-w-xs">
              {meeting.title}
            </h1>
            <span className="text-label-md text-slate-400 font-mono">
              {meeting.meetingCode}
            </span>
          </div>

          <div className="flex items-center gap-3">
            {/* Mode toggle — visible to all, interactive for Host only */}
            <MeetingModeToggle
              meetingId={meeting.id}
              isHost={isHost || isSecretary}
              onModeChanged={handleModeChanged}
            />
          </div>
        </div>

        {/* Join error banner */}
        {joinError && (
          <div
            className="bg-amber-50 border-b border-amber-200 px-4 py-2 text-body-sm text-amber-800"
            role="alert"
          >
            {joinError}
          </div>
        )}

        {/* Jitsi iframe */}
        <div className="flex-1 min-h-0">
          <JitsiFrame
            ref={jitsiRef}
            meetingCode={meeting.meetingCode}
            displayName={user?.username ?? 'Khách'}
            jwt={useAuthStore.getState().token ?? undefined}
            onVideoConferenceLeft={handleVideoConferenceLeft}
            className="w-full h-full"
          />
        </div>
      </div>

      {/* ── Sidebar ── */}
      <div
        className="w-72 bg-white border-l border-slate-200 flex flex-col shrink-0"
        aria-label="Thanh bên cuộc họp"
      >
        {/* Sidebar tabs */}
        <div className="flex border-b border-outline-variant overflow-x-auto shrink-0">
          {sidebarTabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setSidebarTab(tab.key)}
              className={`flex items-center gap-1 px-3 py-2.5 text-label-md font-medium whitespace-nowrap
                          border-b-2 transition-colors flex-1 justify-center
                          ${sidebarTab === tab.key
                            ? 'border-primary text-primary'
                            : 'border-transparent text-on-surface-variant hover:text-on-surface'
                          }`}
              aria-selected={sidebarTab === tab.key}
              role="tab"
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                {tab.icon}
              </span>
              <span className="hidden sm:inline">{tab.label}</span>
            </button>
          ))}
        </div>

        {/* Sidebar content */}
        <div className="flex-1 overflow-y-auto" role="tabpanel">
          {sidebarTab === 'participants' && (
            <div className="py-2">
              <ParticipantList currentUserId={user?.id} />
            </div>
          )}

          {sidebarTab === 'raise-hand' && (
            <RaiseHandPanel
              meetingId={meeting.id}
              isHost={isHost || isSecretary}
            />
          )}

          {sidebarTab === 'transcription' && (
            <TranscriptionPanel
              meetingId={meeting.id}
              isHighPriority={isHighPriority}
            />
          )}
        </div>
      </div>
    </div>
  )
}
