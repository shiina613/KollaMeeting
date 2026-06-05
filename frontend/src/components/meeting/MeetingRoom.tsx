/**
 * MeetingRoom — main container for an active meeting session.
 *
 * Combines:
 * - JitsiFrame (video conference)
 * - TopBar (meeting info and controls)
 * - BottomBar (raise hand, meeting timer)
 * - Sidebar (participants, raise hand panel, transcription)
 * - ToastContainer (event notifications)
 * - ShortcutsHelpOverlay (keyboard shortcuts reference)
 *
 * Responsibilities:
 * - Calls POST /meetings/{id}/join on mount (attendance tracking)
 * - Calls POST /meetings/{id}/leave on unmount
 * - Subscribes to WebSocket meeting events and dispatches to meetingStore
 * - Reacts to mode changes: host/secretary muteAll on MEETING_MODE; others muteLocal only
 * - Starts/stops audio capture based on speaking permission and meeting mode
 *
 * Requirements: 4.6, 5.1, 5.3, 8.14, 13.1, 13.2, 13.3, 13.4, 13.5
 */

import { useEffect, useRef, useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import JitsiFrame, { type JitsiFrameHandle } from './JitsiFrame'
import TopBar from './TopBar'
import BottomBar from './BottomBar'
import Sidebar, { type SidebarTab } from './Sidebar'
import ToastContainer from './ToastContainer'
import ShortcutsHelpOverlay from './ShortcutsHelpOverlay'
import useWebSocket from '../../hooks/useWebSocket'
import useTranscription from '../../hooks/useTranscription'
import useAuthStore from '../../store/authStore'
import useMeetingStore from '../../store/meetingStore'
import useToastStore, { createToastMessage } from '../../store/toastStore'
import { useJaasToken } from '../../hooks/useJaasToken'
import { useMeetingLifecycle } from '../../hooks/useMeetingLifecycle'
import { useMeetingAudioCapture } from '../../hooks/useMeetingAudioCapture'
import { useKeyboardShortcuts } from '../../hooks/useKeyboardShortcuts'
import { useConnectionQuality } from '../../hooks/useConnectionQuality'
import { useFocusManagement } from '../../hooks/useFocusManagement'
import { leaveMeeting } from '../../services/meetingService'
import type { Meeting, MeetingMode, MeetingEvent } from '../../types/meeting'
import type { Shortcut } from '../../hooks/useKeyboardShortcuts'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MeetingRoomProps {
  meeting: Meeting
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MeetingRoom
 *
 * Requirements: 4.6, 5.1, 5.3, 8.14, 13.1, 13.2, 13.3, 13.4, 13.5
 */
export default function MeetingRoom({ meeting }: MeetingRoomProps) {
  const navigate = useNavigate()
  const { user } = useAuthStore()
  const { handleMeetingEvent, mode } = useMeetingStore()
  const {
    segments,
    isTranscriptionAvailable,
    handleTranscriptionEvent,
    clearSegments,
  } = useTranscription()

  const jitsiRef = useRef<JitsiFrameHandle>(null)
  const sidebarRef = useRef<HTMLDivElement>(null)
  const sidebarToggleRef = useRef<HTMLButtonElement>(null)

  const [sidebarTab, setSidebarTab] = useState<SidebarTab>('participants')
  const [isSidebarOpen, setIsSidebarOpen] = useState(true)
  /** True after Jitsi `videoConferenceJoined` — host/secretary mode switch is shown only then. */
  const [conferenceReady, setConferenceReady] = useState(false)
  const [showShortcutsHelp, setShowShortcutsHelp] = useState(false)

  // ── Dismissible error banner state (Req 12.1–12.4) ────────────────────────

  /** The currently visible error message (only one at a time). */
  const [activeError, setActiveError] = useState<{ message: string; type: 'join' | 'permission' } | null>(null)
  /** Whether the banner is fading out (opacity transition). */
  const [isErrorFading, setIsErrorFading] = useState(false)
  const errorDismissTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const errorFadeTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  // ── Derived state ──────────────────────────────────────────────────────────

  const isHost = user?.id === (meeting.hostUser?.id ?? meeting.hostId)
  const isSecretary = user?.id === (meeting.secretaryUser?.id ?? meeting.secretaryId)
  const isHighPriority = meeting.transcriptionPriority === 'HIGH_PRIORITY'
  const isMeetingMode = mode === 'MEETING_MODE'

  // ── Error banner management (Req 12.1–12.4) ─────────────────────────────────

  /** Clear all error timers. */
  const clearErrorTimers = useCallback(() => {
    if (errorDismissTimerRef.current) {
      clearTimeout(errorDismissTimerRef.current)
      errorDismissTimerRef.current = null
    }
    if (errorFadeTimerRef.current) {
      clearTimeout(errorFadeTimerRef.current)
      errorFadeTimerRef.current = null
    }
  }, [])

  /** Show an error banner, replacing any existing one (Req 12.4). */
  const showError = useCallback(
    (message: string, type: 'join' | 'permission') => {
      clearErrorTimers()
      setIsErrorFading(false)
      setActiveError({ message, type })

      // Auto-dismiss after 10 seconds (Req 12.2)
      errorDismissTimerRef.current = setTimeout(() => {
        setIsErrorFading(true)
        // After fade-out animation completes, remove the banner
        errorFadeTimerRef.current = setTimeout(() => {
          setActiveError(null)
          setIsErrorFading(false)
        }, 300)
      }, 10000)
    },
    [clearErrorTimers],
  )

  /** Dismiss the error banner immediately (Req 12.3). */
  const dismissError = useCallback(() => {
    clearErrorTimers()
    setIsErrorFading(true)
    errorFadeTimerRef.current = setTimeout(() => {
      setActiveError(null)
      setIsErrorFading(false)
    }, 300)
  }, [clearErrorTimers])

  // Clean up error timers on unmount
  useEffect(() => {
    return () => {
      clearErrorTimers()
    }
  }, [clearErrorTimers])

  // ── useJaasToken — replaces inline JaaS token logic (Req 13.3) ─────────────

  const { token: jaasToken, roomName: jaasRoomName, isLoading: jaasLoading, error: jaasError, retry: retryJaasToken } = useJaasToken(meeting.id)

  // ── useMeetingLifecycle — replaces inline join/leave logic (Req 13.4) ──────

  useMeetingLifecycle({
    meeting,
    onJoinError: (message) => showError(message, 'join'),
  })

  // ── useMeetingAudioCapture — replaces inline audio capture (Req 13.5) ──────

  const { isCapturing, isPermissionDenied, handleAudioMuteStatusChanged } = useMeetingAudioCapture({
    meeting,
    jitsiRef,
    isHost,
    isSecretary,
  })

  // Show permission denied error via the unified error banner (Req 12.4)
  useEffect(() => {
    if (isPermissionDenied) {
      showError(
        'Trình duyệt đã chặn quyền truy cập microphone. Vui lòng cấp quyền trong cài đặt trình duyệt để phiên âm hoạt động.',
        'permission',
      )
    }
  }, [isPermissionDenied, showError])

  // ── useConnectionQuality — connection stats for indicator (Req 16) ─────────

  const { stats: connectionStats } = useConnectionQuality({
    jitsiRef,
    interval: 5000,
  })

  // ── useFocusManagement — sidebar focus management (Req 8) ──────────────────

  const isMobileOverlay = typeof window !== 'undefined' && window.innerWidth < 768

  useFocusManagement({
    isOpen: isSidebarOpen,
    isMobileOverlay,
    containerRef: sidebarRef,
    returnFocusRef: sidebarToggleRef,
    onClose: () => setIsSidebarOpen(false),
  })

  // ── useKeyboardShortcuts — keyboard shortcuts (Req 7) ──────────────────────

  const shortcuts: Shortcut[] = [
    {
      key: 's',
      altKey: true,
      action: () => setIsSidebarOpen((prev) => !prev),
      description: 'Toggle sidebar',
      enabled: true,
    },
    {
      key: 'h',
      altKey: true,
      action: () => {
        // Trigger raise/lower hand — only relevant for non-host in MEETING_MODE
        if (!isHost && !isSecretary && user?.id) {
          const raiseHandBtn = document.querySelector('[data-testid="raise-hand-button"]') as HTMLButtonElement | null
          raiseHandBtn?.click()
        }
      },
      description: 'Raise/lower hand',
      enabled: !isHost && !isSecretary && isMeetingMode,
    },
    {
      key: 't',
      altKey: true,
      action: () => {
        if (isHighPriority) {
          setSidebarTab('transcription')
          if (!isSidebarOpen) setIsSidebarOpen(true)
        }
      },
      description: 'Switch to transcription tab',
      enabled: isHighPriority,
    },
    {
      key: '?',
      altKey: true,
      shiftKey: true,
      action: () => setShowShortcutsHelp((prev) => !prev),
      description: 'Show shortcuts help',
      enabled: true,
    },
  ]

  useKeyboardShortcuts({
    shortcuts,
    enabled: !showShortcutsHelp,
  })

  // ── Clear segments on unmount ──────────────────────────────────────────────

  useEffect(() => {
    return () => {
      clearSegments()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // ── WebSocket event handler ────────────────────────────────────────────────

  const onMeetingEvent = useCallback(
    (event: MeetingEvent) => {
      // Capture pre-dispatch state for events that need it
      const prevSpeakingPermission = useMeetingStore.getState().speakingPermission

      // Dispatch to store first
      handleMeetingEvent(event)
      // Dispatch transcription-related events to the transcription hook
      handleTranscriptionEvent(event)

      // Dispatch toast notifications for relevant events (Req 5.1, 5.2, 5.3)
      const toastPayload = createToastMessage(event)
      if (toastPayload) {
        useToastStore.getState().addToast(toastPayload)
      }

      // React to mode changes for Jitsi audio control
      if (event.type === 'MODE_CHANGED') {
        const newMode = (event.payload as { mode: MeetingMode }).mode
        if (newMode === 'MEETING_MODE') {
          if (isHost || isSecretary) {
            jitsiRef.current?.muteAll()
          } else {
            jitsiRef.current?.muteLocal()
          }
        }
      }

      // SPEAKING_PERMISSION_GRANTED
      if (event.type === 'SPEAKING_PERMISSION_GRANTED') {
        const { userId } = event.payload as {
          userId: number
          userName: string
          speakerTurnId: string
        }
        if (user?.id === userId) {
          if (isHost || isSecretary) {
            jitsiRef.current?.muteAll()
            setTimeout(() => {
              jitsiRef.current?.unmuteLocal()
            }, 300)
          } else {
            setTimeout(() => {
              jitsiRef.current?.unmuteLocal()
            }, 300)
          }
        } else {
          jitsiRef.current?.muteLocal()
        }
      }

      // SPEAKING_PERMISSION_REVOKED
      if (event.type === 'SPEAKING_PERMISSION_REVOKED') {
        if (user?.id !== undefined && prevSpeakingPermission?.userId === user.id) {
          jitsiRef.current?.muteLocal()
        }
      }

      // When meeting ends, navigate back to meeting detail
      if (event.type === 'MEETING_ENDED') {
        navigate(`/meetings/${meeting.id}`, { replace: true })
      }
    },
    [handleMeetingEvent, handleTranscriptionEvent, meeting.id, navigate, user?.id, isHost, isSecretary],
  )

  useWebSocket({
    meetingId: meeting.id,
    onMeetingEvent,
  })

  // ── Mode change handler (from toggle button) ───────────────────────────────

  const handleModeChanged = useCallback((_newMode: MeetingMode) => {
    // No-op: Jitsi mute and audio capture are handled by the onMeetingEvent
    // WS handler when it receives the MODE_CHANGED broadcast.
  }, [])

  // ── Jitsi event handlers ───────────────────────────────────────────────────

  const handleVideoConferenceLeft = useCallback(() => {
    setConferenceReady(false)
    leaveMeeting(meeting.id).catch(() => {})
    navigate(`/meetings/${meeting.id}`, { replace: true })
  }, [meeting.id, navigate])

  const handleVideoConferenceJoined = useCallback(() => {
    setConferenceReady(true)
  }, [])

  // ── JaaS enabled check ────────────────────────────────────────────────────

  const IS_JAAS = (import.meta.env.VITE_JAAS_APP_ID ?? '').length > 0

  // ── Video area content (JaaS loading / error / Jitsi frame) ────────────────

  const renderVideoContent = () => {
    if (IS_JAAS && jaasLoading) {
      return (
        <div
          className="w-full h-full flex flex-col items-center justify-center bg-slate-900 text-white"
          aria-live="polite"
          data-testid="jaas-loading"
        >
          <div className="w-10 h-10 border-4 border-white border-t-transparent rounded-full animate-spin mb-4" />
          <p className="text-body-sm text-slate-300">Đang kết nối JaaS...</p>
        </div>
      )
    }
    if (IS_JAAS && jaasError) {
      return (
        <div
          className="w-full h-full flex flex-col items-center justify-center bg-slate-900 text-white gap-4"
          data-testid="jaas-error"
        >
          <span className="material-symbols-outlined text-5xl text-red-400" aria-hidden="true">
            error
          </span>
          <p className="text-body-sm text-slate-300">{jaasError}</p>
          <button
            onClick={retryJaasToken}
            className="px-4 py-2 bg-primary text-white rounded-lg text-body-sm font-medium hover:bg-primary/90 transition-colors"
            data-testid="jaas-retry-button"
          >
            Thử lại
          </button>
        </div>
      )
    }
    return (
      <JitsiFrame
        ref={jitsiRef}
        meetingCode={IS_JAAS && jaasRoomName ? jaasRoomName : meeting.meetingCode}
        displayName={user?.fullName ?? user?.username ?? 'Khách'}
        jwt={IS_JAAS ? jaasToken ?? undefined : undefined}
        onVideoConferenceJoined={handleVideoConferenceJoined}
        onVideoConferenceLeft={handleVideoConferenceLeft}
        onAudioMuteStatusChanged={handleAudioMuteStatusChanged}
        className="w-full h-full"
      />
    )
  }

  // ── Render ─────────────────────────────────────────────────────────────────

  return (
    <div
      className="flex h-screen bg-slate-900 overflow-hidden"
      data-testid="meeting-room"
      aria-label={`Phòng họp: ${meeting.title}`}
    >
      <div className="flex-1 flex flex-col min-w-0">
        <TopBar
          meeting={meeting}
          isHost={isHost}
          isSecretary={isSecretary}
          conferenceReady={conferenceReady}
          isCapturing={isCapturing}
          isSidebarOpen={isSidebarOpen}
          onToggleSidebar={() => setIsSidebarOpen((prev) => !prev)}
          onModeChanged={handleModeChanged}
          connectionStats={connectionStats}
          sidebarToggleRef={sidebarToggleRef}
        />

        {activeError && (
          <div
            className={`flex items-center gap-2 px-4 py-2 text-body-sm border-b transition-opacity duration-300 ${
              isErrorFading ? 'opacity-0' : 'opacity-100'
            } ${
              activeError.type === 'permission'
                ? 'bg-red-50 border-red-200 text-red-800'
                : 'bg-amber-50 border-amber-200 text-amber-800'
            }`}
            role="alert"
            data-testid="error-banner"
          >
            {activeError.type === 'permission' && (
              <span className="material-symbols-outlined text-[16px] shrink-0" aria-hidden="true">mic_off</span>
            )}
            <span className="flex-1">{activeError.message}</span>
            <button
              onClick={dismissError}
              className={`shrink-0 p-1 rounded hover:bg-black/10 transition-colors ${
                activeError.type === 'permission' ? 'text-red-600' : 'text-amber-600'
              }`}
              aria-label="Dismiss error"
              data-testid="error-banner-dismiss"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span>
            </button>
          </div>
        )}

        <div className="relative flex-1 min-h-0">
          {renderVideoContent()}
          <ToastContainer />
        </div>

        <BottomBar meeting={meeting} isHost={isHost} isSecretary={isSecretary} currentUserId={user?.id} />
      </div>

      <Sidebar
        isOpen={isSidebarOpen}
        onClose={() => setIsSidebarOpen(false)}
        activeTab={sidebarTab}
        onTabChange={setSidebarTab}
        meeting={meeting}
        isHost={isHost}
        isSecretary={isSecretary}
        currentUserId={user?.id}
        segments={segments}
        isTranscriptionAvailable={isTranscriptionAvailable}
        sidebarRef={sidebarRef}
        toggleButtonRef={sidebarToggleRef}
      />

      <ShortcutsHelpOverlay isOpen={showShortcutsHelp} onClose={() => setShowShortcutsHelp(false)} />
    </div>
  )
}
