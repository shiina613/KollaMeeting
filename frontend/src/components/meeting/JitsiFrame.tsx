/**
 * JitsiFrame — embeds the Jitsi Meet IFrame and exposes audio controls.
 *
 * Renders a container div that the Jitsi IFrame API mounts into.
 * Exposes mute/unmute/muteAll controls via an imperative ref handle.
 *
 * Requirements: 4.7
 */

import { useRef, forwardRef, useImperativeHandle } from 'react'
import useJitsiApi, {
  type JitsiParticipantEvent,
} from '../../hooks/useJitsiApi'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface JitsiFrameHandle {
  /** Mute the local user's microphone */
  mute: () => void
  /** Unmute the local user's microphone */
  unmute: () => void
  /** Mute all participants (Host action) */
  muteAll: () => void
  /** Whether the Jitsi API is ready */
  isReady: boolean
}

export interface JitsiFrameProps {
  /** Meeting code used as the Jitsi room name */
  meetingCode: string
  /** Display name shown in the Jitsi UI */
  displayName: string
  /** Optional avatar URL */
  avatarUrl?: string
  /** JWT token for authenticated Jitsi rooms */
  jwt?: string
  /** Called when a participant joins */
  onParticipantJoined?: (event: JitsiParticipantEvent) => void
  /** Called when a participant leaves */
  onParticipantLeft?: (event: JitsiParticipantEvent) => void
  /** Called when the local user leaves the conference */
  onVideoConferenceLeft?: () => void
  /** Additional CSS class for the container */
  className?: string
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * JitsiFrame embeds the Jitsi Meet video conference.
 *
 * Use the ref handle to control audio from the parent:
 * ```tsx
 * const jitsiRef = useRef<JitsiFrameHandle>(null)
 * jitsiRef.current?.muteAll()
 * ```
 *
 * Requirements: 4.7
 */
const JitsiFrame = forwardRef<JitsiFrameHandle, JitsiFrameProps>(
  function JitsiFrame(
    {
      meetingCode,
      displayName,
      avatarUrl,
      jwt,
      onParticipantJoined,
      onParticipantLeft,
      onVideoConferenceLeft,
      className = '',
    },
    ref,
  ) {
    const containerRef = useRef<HTMLDivElement>(null)

    const { isReady, mute, unmute, muteAll } = useJitsiApi({
      containerRef,
      meetingCode,
      displayName,
      avatarUrl,
      jwt,
      onParticipantJoined,
      onParticipantLeft,
      onVideoConferenceLeft,
    })

    // Expose controls to parent via ref
    useImperativeHandle(
      ref,
      () => ({
        mute,
        unmute,
        muteAll,
        isReady,
      }),
      [mute, unmute, muteAll, isReady],
    )

    return (
      <div className={`relative w-full h-full ${className}`}>
        {/* Loading overlay — shown until Jitsi API is ready */}
        {!isReady && (
          <div
            className="absolute inset-0 flex flex-col items-center justify-center
                       bg-slate-900 text-white z-10"
            aria-live="polite"
            aria-label="Đang tải phòng họp"
          >
            <div className="w-10 h-10 border-4 border-white border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-body-sm text-slate-300">Đang kết nối phòng họp...</p>
          </div>
        )}

        {/* Jitsi mounts its iframe into this div */}
        <div
          ref={containerRef}
          className="w-full h-full"
          data-testid="jitsi-container"
          aria-label="Phòng họp video"
        />
      </div>
    )
  },
)

export default JitsiFrame
