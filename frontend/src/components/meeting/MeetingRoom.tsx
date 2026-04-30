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
 * - Starts/stops audio capture based on speaking permission and meeting mode
 *
 * Requirements: 4.6, 5.1, 5.3, 8.14
 */

import { useEffect, useRef, useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import JitsiFrame, { type JitsiFrameHandle } from './JitsiFrame'
import TranscriptionPanel from './TranscriptionPanel'
import TranscriptionPriorityControl from './TranscriptionPriorityControl'
import RaiseHandPanel from './RaiseHandPanel'
import RaiseHandButton from './RaiseHandButton'
import MeetingModeToggle from './MeetingModeToggle'
import ParticipantList from './ParticipantList'
import SpeakingPermissionBadge from './SpeakingPermissionBadge'
import useWebSocket from '../../hooks/useWebSocket'
import useTranscription from '../../hooks/useTranscription'
import useAudioCapture from '../../hooks/useAudioCapture'
import useAuthStore from '../../store/authStore'
import useMeetingStore from '../../store/meetingStore'
import { joinMeeting, leaveMeeting } from '../../services/meetingService'
import type { Meeting, MeetingMode, MeetingEvent, TranscriptionPriority } from '../../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MeetingRoomProps {
  meeting: Meeting
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MeetingRoom
 *
 * Requirements: 4.6, 5.1, 5.3, 8.14
 */
export default function MeetingRoom({ meeting }: MeetingRoomProps) {
  const navigate = useNavigate()
  const { user } = useAuthStore()
  const { setActiveMeeting, clearActiveMeeting, handleMeetingEvent, mode } = useMeetingStore()
  const { handleTranscriptionEvent, clearSegments } = useTranscription()

  const jitsiRef = useRef<JitsiFrameHandle>(null)
  const hasJoinedRef = useRef(false)
  const [sidebarTab, setSidebarTab] = useState<'participants' | 'transcription' | 'raise-hand'>('participants')
  const [joinError, setJoinError] = useState<string | null>(null)
  const [currentPriority, setCurrentPriority] = useState<TranscriptionPriority>(
    meeting.transcriptionPriority,
  )

  // ── Audio capture state ────────────────────────────────────────────────────

  // speakerTurnId is set when the local user receives SPEAKING_PERMISSION_GRANTED.
  // In FREE_MODE, a stable turn ID is used so all participants can stream.
  const [speakerTurnId, setSpeakerTurnId] = useState<string | null>(null)

  // ── Derived state ──────────────────────────────────────────────────────────

  const isHost = user?.id === meeting.hostUser?.id
  const isSecretary = user?.id === meeting.secretaryUser?.id
  const isHighPriority = currentPriority === 'HIGH_PRIORITY'
  const isMeetingMode = mode === 'MEETING_MODE'
  const canChangePriority =
    user?.role === 'ADMIN' || user?.role === 'SECRETARY'

  // ── Audio capture hook ─────────────────────────────────────────────────────

  const { startCapture, stopCapture, isCapturing, isPermissionDenied } = useAudioCapture({
    meetingId: meeting.id,
    speakerTurnId,
    onError: (err) => {
      console.error('[MeetingRoom] Audio capture error:', err.message)
    },
  })

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
      clearSegments()
    }
  }, [meeting.id]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── WebSocket event handler ────────────────────────────────────────────────

  const onMeetingEvent = useCallback(
    (event: MeetingEvent) => {
      // Capture pre-dispatch state for events that need it
      const prevSpeakingPermission = useMeetingStore.getState().speakingPermission

      // Dispatch to store first
      handleMeetingEvent(event)
      // Dispatch transcription-related events to the transcription hook
      handleTranscriptionEvent(event)

      // React to mode changes for Jitsi audio control
      if (event.type === 'MODE_CHANGED') {
        const newMode = (event.payload as { mode: MeetingMode }).mode
        if (newMode === 'MEETING_MODE') {
          // Mute all participants when entering Meeting Mode (Req 21.4)
          jitsiRef.current?.muteAll()
          // Stop audio capture for all participants — only the granted speaker
          // will capture in MEETING_MODE (started on SPEAKING_PERMISSION_GRANTED)
          stopCapture()
          setSpeakerTurnId(null)
        } else {
          // FREE_MODE: all participants can capture simultaneously (Req 21.5)
          // Generate a stable turn ID for this free-mode session
          const freeTurnId = `free-${meeting.id}-${user?.id ?? 'anon'}-${Date.now()}`
          setSpeakerTurnId(freeTurnId)
          startCapture()
        }
      }

      // SPEAKING_PERMISSION_GRANTED:
      // - Mute all participants first (Req 4.9)
      // - If the local user is the granted speaker → unmute + start audio capture (Req 4.10, 22.5, 8.14)
      if (event.type === 'SPEAKING_PERMISSION_GRANTED') {
        const { userId, speakerTurnId: newTurnId } = event.payload as {
          userId: number
          userName: string
          speakerTurnId: string
        }
        // Mute everyone first (Req 4.9)
        jitsiRef.current?.muteAll()
        if (user?.id === userId) {
          // Unmute the local speaker in Jitsi (Req 4.10)
          jitsiRef.current?.unmute()
          // Start audio capture with the new speaker turn ID (Req 8.14)
          setSpeakerTurnId(newTurnId)
          startCapture()
        } else {
          // Another user got permission — stop our capture if running
          stopCapture()
          setSpeakerTurnId(null)
        }
      }

      // SPEAKING_PERMISSION_REVOKED:
      // - If the local user was the speaker → mute + stop audio capture (Req 4.9, 22.5, 8.14)
      if (event.type === 'SPEAKING_PERMISSION_REVOKED') {
        if (user?.id !== undefined && prevSpeakingPermission?.userId === user.id) {
          jitsiRef.current?.mute()
          stopCapture()
          setSpeakerTurnId(null)
        }
      }

      // When meeting ends, navigate back to meeting detail
      if (event.type === 'MEETING_ENDED') {
        navigate(`/meetings/${meeting.id}`, { replace: true })
      }
    },
    [handleMeetingEvent, handleTranscriptionEvent, meeting.id, navigate, user?.id, startCapture, stopCapture],
  )

  useWebSocket({
    meetingId: meeting.id,
    onMeetingEvent,
  })

  // ── Mode change handler (from toggle button) ───────────────────────────────

  const handleModeChanged = useCallback((newMode: MeetingMode) => {
    if (newMode === 'MEETING_MODE') {
      jitsiRef.current?.muteAll()
      // Stop audio capture — only the granted speaker will capture in MEETING_MODE
      stopCapture()
      setSpeakerTurnId(null)
    } else {
      // FREE_MODE: start capturing for all participants
      const freeTurnId = `free-${meeting.id}-${user?.id ?? 'anon'}-${Date.now()}`
      setSpeakerTurnId(freeTurnId)
      startCapture()
    }
  }, [meeting.id, user?.id, startCapture, stopCapture])

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

            {/* Transcription priority control — ADMIN/SECRETARY only */}
            <TranscriptionPriorityControl
              meetingId={meeting.id}
              currentPriority={currentPriority}
              canChangePriority={canChangePriority}
              onPriorityChanged={setCurrentPriority}
            />

            {/* Speaking permission badge — visible to all in MEETING_MODE */}
            <SpeakingPermissionBadge currentUserId={user?.id} />

            {/* Audio capture indicator — shown when actively streaming */}
            {isCapturing && (
              <div
                className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full
                  bg-red-100 border border-red-300 text-label-md font-medium text-red-700"
                aria-label="Đang ghi âm và phiên âm"
                data-testid="audio-capture-indicator"
              >
                <span className="w-2 h-2 rounded-full bg-red-500 animate-pulse" aria-hidden="true" />
                REC
              </div>
            )}
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

        {/* Microphone permission denied banner */}
        {isPermissionDenied && (
          <div
            className="flex items-center gap-2 bg-red-50 border-b border-red-200 px-4 py-2 text-body-sm text-red-800"
            role="alert"
            data-testid="mic-permission-denied-banner"
          >
            <span className="material-symbols-outlined text-[16px] shrink-0" aria-hidden="true">
              mic_off
            </span>
            Trình duyệt đã chặn quyền truy cập microphone. Vui lòng cấp quyền trong cài đặt trình duyệt để phiên âm hoạt động.
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

        {/* Raise hand button — shown to non-host participants in MEETING_MODE */}
        {!isHost && !isSecretary && user?.id && (
          <div className="flex justify-center py-2 bg-slate-800 border-t border-slate-700 shrink-0">
            <RaiseHandButton
              meetingId={meeting.id}
              currentUserId={user.id}
            />
          </div>
        )}
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
