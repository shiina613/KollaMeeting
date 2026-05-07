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
import { fetchJaasToken } from '../../services/jaasService'
import TranscriptionPanel from './TranscriptionPanel'
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
import { joinMeeting, leaveMeeting, revokeSpeakingPermission } from '../../services/meetingService'
import type { Meeting, MeetingMode, MeetingEvent } from '../../types/meeting'

// ─── JaaS config ──────────────────────────────────────────────────────────────

const IS_JAAS = (import.meta.env.VITE_JAAS_APP_ID ?? '').length > 0

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
  const { handleTranscriptionEvent, clearSegments, segments, isTranscriptionAvailable } = useTranscription()

  const jitsiRef = useRef<JitsiFrameHandle>(null)
  const hasJoinedRef = useRef(false)
  const tokenRefreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const [sidebarTab, setSidebarTab] = useState<'participants' | 'transcription' | 'raise-hand'>('participants')
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)
  const [joinError, setJoinError] = useState<string | null>(null)

  // ── Sidebar resize ─────────────────────────────────────────────────────────
  const SIDEBAR_MIN = 200
  const SIDEBAR_MAX = 600
  const SIDEBAR_DEFAULT = 288 // w-72 = 18rem = 288px
  const [sidebarWidth, setSidebarWidth] = useState(SIDEBAR_DEFAULT)
  const isDraggingRef = useRef(false)
  const dragStartXRef = useRef(0)
  const dragStartWidthRef = useRef(SIDEBAR_DEFAULT)

  const handleResizeMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault()
    isDraggingRef.current = true
    dragStartXRef.current = e.clientX
    dragStartWidthRef.current = sidebarWidth

    const onMouseMove = (ev: MouseEvent) => {
      if (!isDraggingRef.current) return
      // Dragging left = increasing width (sidebar is on the right)
      const delta = dragStartXRef.current - ev.clientX
      const newWidth = Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, dragStartWidthRef.current + delta))
      setSidebarWidth(newWidth)
    }

    const onMouseUp = () => {
      isDraggingRef.current = false
      window.removeEventListener('mousemove', onMouseMove)
      window.removeEventListener('mouseup', onMouseUp)
      document.body.style.cursor = ''
      document.body.style.userSelect = ''
    }

    document.body.style.cursor = 'col-resize'
    document.body.style.userSelect = 'none'
    window.addEventListener('mousemove', onMouseMove)
    window.addEventListener('mouseup', onMouseUp)
  }, [sidebarWidth])

  // ── JaaS token state ───────────────────────────────────────────────────────

  const [jaasToken, setJaasToken] = useState<string | null>(null)
  const [jaasRoomName, setJaasRoomName] = useState<string | null>(null)
  const [jaasLoading, setJaasLoading] = useState<boolean>(IS_JAAS)
  const [jaasError, setJaasError] = useState<string | null>(null)

  // ── Audio capture state ────────────────────────────────────────────────────

  // In MEETING_MODE, audio capture is driven by the local mic state (audioMuteStatusChanged).
  // speakerTurnId is auto-generated each time the mic turns ON.
  // Host/Secretary can unmute directly; members must have permission granted first.
  const [speakerTurnId, setSpeakerTurnId] = useState<string | null>(null)

  // Refs to avoid stale closures in the Jitsi event callback
  const modeRef = useRef(mode)
  const isHostRef = useRef(false)
  const isSecretaryRef = useRef(false)

  // ── Derived state ──────────────────────────────────────────────────────────

  const isHost = user?.id === (meeting.hostUser?.id ?? meeting.hostId)
  const isSecretary = user?.id === (meeting.secretaryUser?.id ?? meeting.secretaryId)
  // Priority is fixed at meeting creation — cannot change during an active meeting.
  const isHighPriority = meeting.transcriptionPriority === 'HIGH_PRIORITY'
  const isMeetingMode = mode === 'MEETING_MODE'

  // Keep refs up-to-date so the Jitsi callback always reads latest values
  modeRef.current = mode
  isHostRef.current = isHost
  isSecretaryRef.current = isSecretary

  // ── Audio capture hook ─────────────────────────────────────────────────────

  const { startCapture, stopCapture, isCapturing, isPermissionDenied } = useAudioCapture({
    meetingId: meeting.id,
    speakerTurnId,
    onError: (err) => {
      console.error('[MeetingRoom] Audio capture error:', err.message)
    },
  })

  // ── Mic state → audio capture ──────────────────────────────────────────────

  /**
   * Called by JitsiFrame whenever the LOCAL user's mic mute state changes.
   * Rules:
   *  - Only MEETING_MODE triggers STT recording. FREE_MODE = no recording.
   *  - mic ON  (isMuted=false) → generate a new turn ID → start audio capture
   *  - mic OFF (isMuted=true)  → stop audio capture (sends FINALIZE to backend)
   *  - Host/Secretary turning ON: auto-revoke any current speaking permission
   *    so the previous member speaker is silenced in the permission system too.
   */
  const handleAudioMuteStatusChanged = useCallback((isMuted: boolean) => {
    if (modeRef.current !== 'MEETING_MODE') {
      // FREE_MODE: no STT, just stop any stray capture
      if (!isMuted) stopCapture()
      return
    }

    if (isMuted) {
      // Mic turned OFF → finalize and stop recording
      stopCapture()
      setSpeakerTurnId(null)
    } else {
      // Mic turned ON in MEETING_MODE → start recording
      // If Host/Secretary, revoke any current member speaking permission first
      // so the previous speaker knows they've been superseded.
      if (isHostRef.current || isSecretaryRef.current) {
        // Only revoke if someone actually has permission (avoids 400 from backend)
        const activePerm = useMeetingStore.getState().speakingPermission
        if (activePerm) {
          revokeSpeakingPermission(meeting.id).catch(() => { /* non-critical */ })
        }
      }
      const newTurnId = `${meeting.id}-${Date.now()}`
      setSpeakerTurnId(newTurnId)
      startCapture(newTurnId)
    }
  }, [meeting.id, startCapture, stopCapture])

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

  // ── JaaS token fetch ───────────────────────────────────────────────────────

  async function fetchToken() {
    setJaasLoading(true)
    setJaasError(null)
    try {
      const { token, roomName } = await fetchJaasToken(meeting.id)
      setJaasToken(token)
      setJaasRoomName(roomName)
      // Schedule refresh at 55-minute mark (token expires in 60 min)
      tokenRefreshTimerRef.current = setTimeout(fetchToken, 55 * 60 * 1000)
    } catch (err) {
      setJaasError('Không thể lấy token JaaS. Vui lòng thử lại.')
    } finally {
      setJaasLoading(false)
    }
  }

  useEffect(() => {
    if (!IS_JAAS) return
    fetchToken()
    return () => {
      if (tokenRefreshTimerRef.current) clearTimeout(tokenRefreshTimerRef.current)
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
          // Entering MEETING_MODE: mute everyone — mic permission required to speak.
          jitsiRef.current?.muteAll()
          stopCapture()
          setSpeakerTurnId(null)
        } else {
          // Returning to FREE_MODE: unmute everyone. No STT in free mode.
          stopCapture()
          setSpeakerTurnId(null)
        }
      }

      // SPEAKING_PERMISSION_GRANTED:
      // Backend granted mic permission to a specific user.
      // For the granted user: unmute their Jitsi mic → audioMuteStatusChanged fires → startCapture.
      // For others: ensure they are muted.
      if (event.type === 'SPEAKING_PERMISSION_GRANTED') {
        const { userId } = event.payload as {
          userId: number
          userName: string
          speakerTurnId: string
        }
        if (user?.id === userId) {
          // I was granted permission — unmute my Jitsi mic.
          // audioMuteStatusChanged(isMuted=false) will fire and startCapture.
          jitsiRef.current?.muteAll()
          setTimeout(() => {
            jitsiRef.current?.unmuteLocal()
          }, 300)
        } else {
          // Another user got permission — ensure I am muted.
          jitsiRef.current?.muteLocal()
          stopCapture()
          setSpeakerTurnId(null)
        }
      }

      // SPEAKING_PERMISSION_REVOKED:
      // The speaker's mic turns OFF — audioMuteStatusChanged(isMuted=true) will fire
      // and stopCapture() will be called from handleAudioMuteStatusChanged.
      // We also explicitly mute via Jitsi to ensure the mic is off.
      if (event.type === 'SPEAKING_PERMISSION_REVOKED') {
        if (user?.id !== undefined && prevSpeakingPermission?.userId === user.id) {
          jitsiRef.current?.muteLocal()
          // stopCapture is handled by audioMuteStatusChanged, but call explicitly
          // as fallback in case the Jitsi event is delayed.
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

  const handleModeChanged = useCallback((_newMode: MeetingMode) => {
    // No-op: Jitsi mute and audio capture are handled by the onMeetingEvent
    // WS handler when it receives the MODE_CHANGED broadcast. This avoids
    // duplicate muteAll calls (the toggle API response arrives before the WS
    // event, causing muteEveryone to fire twice).
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

            {/* Sidebar toggle */}
            <button
              onClick={() => setIsSidebarOpen((prev) => !prev)}
              className="p-1.5 rounded-md text-slate-300 hover:text-white hover:bg-slate-700 transition-colors"
              aria-label={isSidebarOpen ? 'Ẩn thanh bên' : 'Hiện thanh bên'}
              data-testid="sidebar-toggle"
            >
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
                {isSidebarOpen ? 'right_panel_close' : 'right_panel_open'}
              </span>
            </button>
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
          {IS_JAAS && jaasLoading ? (
            <div
              className="w-full h-full flex flex-col items-center justify-center bg-slate-900 text-white"
              aria-live="polite"
              data-testid="jaas-loading"
            >
              <div className="w-10 h-10 border-4 border-white border-t-transparent rounded-full animate-spin mb-4" />
              <p className="text-body-sm text-slate-300">Đang kết nối JaaS...</p>
            </div>
          ) : IS_JAAS && jaasError ? (
            <div
              className="w-full h-full flex flex-col items-center justify-center bg-slate-900 text-white gap-4"
              data-testid="jaas-error"
            >
              <span className="material-symbols-outlined text-5xl text-red-400" aria-hidden="true">
                error
              </span>
              <p className="text-body-sm text-slate-300">{jaasError}</p>
              <button
                onClick={fetchToken}
                className="px-4 py-2 bg-primary text-white rounded-lg text-body-sm font-medium hover:bg-primary/90 transition-colors"
                data-testid="jaas-retry-button"
              >
                Thử lại
              </button>
            </div>
          ) : (
            <JitsiFrame
              ref={jitsiRef}
              meetingCode={IS_JAAS && jaasRoomName ? jaasRoomName : meeting.meetingCode}
              displayName={user?.fullName ?? user?.username ?? 'Khách'}
              jwt={IS_JAAS ? jaasToken ?? undefined : undefined}
              onVideoConferenceLeft={handleVideoConferenceLeft}
              onAudioMuteStatusChanged={handleAudioMuteStatusChanged}
              className="w-full h-full"
            />
          )}
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

      {/* ── Sidebar (resizable) ── */}
      {isSidebarOpen && (
        <div
          className="relative bg-white border-l border-slate-200 flex flex-col shrink-0"
          style={{ width: sidebarWidth }}
          aria-label="Thanh bên cuộc họp"
          data-testid="meeting-sidebar"
        >
          {/* Drag handle — left edge */}
          <div
            onMouseDown={handleResizeMouseDown}
            className="absolute left-0 top-0 bottom-0 w-1 cursor-col-resize z-10
                       hover:bg-primary/40 active:bg-primary/60 transition-colors group"
            aria-hidden="true"
            title="Kéo để thay đổi kích thước"
          >
            {/* Visual indicator dots */}
            <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2
                            flex flex-col gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <span className="w-1 h-1 rounded-full bg-slate-400" />
              <span className="w-1 h-1 rounded-full bg-slate-400" />
              <span className="w-1 h-1 rounded-full bg-slate-400" />
            </div>
          </div>

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
                segments={segments}
                isTranscriptionAvailable={isTranscriptionAvailable}
              />
            )}
          </div>
        </div>
      )}
    </div>
  )
}
