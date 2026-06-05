/**
 * useMeetingAudioCapture — coordinates audio capture with meeting mode and speaking permission.
 *
 * Extracts the audio capture coordination logic from MeetingRoom into a reusable hook:
 * - Tracks whether audio capture is active (`isCapturing`)
 * - Tracks microphone permission denial (`isPermissionDenied`)
 * - Coordinates with meeting mode (MEETING_MODE vs FREE_MODE)
 * - In MEETING_MODE: only captures when the user has speaking permission (speakerTurnId)
 * - In FREE_MODE: no STT recording (stops any stray capture)
 * - Handles the `handleAudioMuteStatusChanged` callback from Jitsi
 * - Host/secretary auto-revokes speaking permission when they unmute
 *
 * Requirements: 13.5
 */

import { useCallback, useRef, useState } from 'react'
import useAudioCapture from './useAudioCapture'
import useMeetingStore from '../store/meetingStore'
import { revokeSpeakingPermission } from '../services/meetingService'
import type { Meeting, MeetingMode } from '../types/meeting'
import type { JitsiFrameHandle } from '../components/meeting/JitsiFrame'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseMeetingAudioCaptureOptions {
  /** The active meeting */
  meeting: Meeting
  /** Ref to the Jitsi iframe handle for mute/unmute control */
  jitsiRef: React.RefObject<JitsiFrameHandle>
  /** Whether the current user is the meeting host */
  isHost: boolean
  /** Whether the current user is the meeting secretary */
  isSecretary: boolean
}

export interface UseMeetingAudioCaptureReturn {
  /** Whether audio capture is currently active (streaming to backend) */
  isCapturing: boolean
  /** Whether the microphone permission was denied by the browser */
  isPermissionDenied: boolean
  /** Callback to pass to JitsiFrame's onAudioMuteStatusChanged */
  handleAudioMuteStatusChanged: (isMuted: boolean) => void
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useMeetingAudioCapture
 *
 * Coordinates audio capture with meeting mode and speaking permission state.
 * Encapsulates speaker turn ID management and mode-aware capture start/stop.
 *
 * Requirements: 13.5
 */
export function useMeetingAudioCapture(
  options: UseMeetingAudioCaptureOptions,
): UseMeetingAudioCaptureReturn {
  const { meeting, isHost, isSecretary } = options

  // ── Speaker turn ID state ──────────────────────────────────────────────────
  const [speakerTurnId, setSpeakerTurnId] = useState<string | null>(null)

  // ── Refs to avoid stale closures in the Jitsi event callback ───────────────
  const modeRef = useRef<MeetingMode>(meeting.mode)
  const isHostRef = useRef(isHost)
  const isSecretaryRef = useRef(isSecretary)

  // Keep refs up-to-date so the callback always reads latest values
  const { mode } = useMeetingStore()
  modeRef.current = mode
  isHostRef.current = isHost
  isSecretaryRef.current = isSecretary

  // ── Audio capture hook (low-level mic → WebSocket streaming) ───────────────
  const { startCapture, stopCapture, isCapturing, isPermissionDenied } = useAudioCapture({
    meetingId: meeting.id,
    speakerTurnId,
    onError: (err) => {
      console.error('[useMeetingAudioCapture] Audio capture error:', err.message)
    },
  })

  // ── Mic state → audio capture coordination ─────────────────────────────────

  /**
   * Called by JitsiFrame whenever the LOCAL user's mic mute state changes.
   * Rules:
   *  - Only MEETING_MODE triggers STT recording. FREE_MODE = no recording.
   *  - mic ON  (isMuted=false) → generate a new turn ID → start audio capture
   *  - mic OFF (isMuted=true)  → stop audio capture (sends FINALIZE to backend)
   *  - Host/Secretary turning ON: auto-revoke any current speaking permission
   *    so the previous member speaker is silenced in the permission system too.
   */
  const handleAudioMuteStatusChanged = useCallback(
    (isMuted: boolean) => {
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
          const activePerm = useMeetingStore.getState().speakingPermission
          if (activePerm) {
            revokeSpeakingPermission(meeting.id).catch(() => {
              /* non-critical */
            })
          }
        }
        const newTurnId = `${meeting.id}-${Date.now()}`
        setSpeakerTurnId(newTurnId)
        startCapture(newTurnId)
      }
    },
    [meeting.id, startCapture, stopCapture],
  )

  return {
    isCapturing,
    isPermissionDenied,
    handleAudioMuteStatusChanged,
  }
}

export default useMeetingAudioCapture
