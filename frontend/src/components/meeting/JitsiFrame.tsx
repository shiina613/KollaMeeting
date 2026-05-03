/**
 * JitsiFrame — embeds Jitsi Meet via direct iframe URL.
 *
 * Uses a simple <iframe> pointing to the Jitsi server URL instead of
 * the IFrame API (external_api.js). This avoids script loading issues
 * and works reliably in LAN/self-hosted environments.
 *
 * Audio controls (mute/unmute/muteAll) are no-ops in this mode —
 * users control audio directly within the Jitsi UI.
 *
 * Requirements: 4.7
 */

import { forwardRef, useImperativeHandle, useCallback, useState, useEffect } from 'react'
import type { JitsiParticipantEvent } from '../../hooks/useJitsiApi'

const JITSI_URL = import.meta.env.VITE_JITSI_URL ?? 'https://meet.jit.si'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface JitsiFrameHandle {
  mute: () => void
  unmute: () => void
  muteAll: () => void
  isReady: boolean
}

export interface JitsiFrameProps {
  meetingCode: string
  displayName: string
  avatarUrl?: string
  jwt?: string
  onParticipantJoined?: (event: JitsiParticipantEvent) => void
  onParticipantLeft?: (event: JitsiParticipantEvent) => void
  onVideoConferenceLeft?: () => void
  className?: string
}

// ─── Component ────────────────────────────────────────────────────────────────

const JitsiFrame = forwardRef<JitsiFrameHandle, JitsiFrameProps>(
  function JitsiFrame({ meetingCode, displayName, className = '' }, ref) {
    const [isReady, setIsReady] = useState(false)

    // Mark ready after iframe loads
    const handleLoad = useCallback(() => {
      setIsReady(true)
    }, [])

    // Fallback: mark ready after 5s regardless
    useEffect(() => {
      const t = setTimeout(() => setIsReady(true), 5_000)
      return () => clearTimeout(t)
    }, [])

    // Build Jitsi URL with config params
    // Note: userInfo.displayName must NOT be in the URL fragment for meet.jit.si
    // — it causes JSON parse errors. Display name is set via prejoin or Jitsi UI.
    const jitsiSrc = [
      `${JITSI_URL}/${encodeURIComponent(meetingCode)}`,
      `#config.startWithAudioMuted=false`,
      `&config.disableDeepLinking=true`,
      `&config.prejoinPageEnabled=false`,
    ].join('')

    // Expose no-op controls — audio is managed within Jitsi UI
    useImperativeHandle(ref, () => ({
      mute: () => {},
      unmute: () => {},
      muteAll: () => {},
      isReady,
    }), [isReady])

    return (
      <div className={`relative w-full h-full ${className}`}>
        {/* Loading overlay */}
        {!isReady && (
          <div
            className="absolute inset-0 flex flex-col items-center justify-center
                       bg-slate-900 text-white z-10"
            aria-live="polite"
          >
            <div className="w-10 h-10 border-4 border-white border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-body-sm text-slate-300">Đang tải phòng họp...</p>
          </div>
        )}

        <iframe
          src={jitsiSrc}
          onLoad={handleLoad}
          allow="camera; microphone; fullscreen; display-capture; autoplay"
          className="w-full h-full border-0"
          title="Phòng họp video"
          data-testid="jitsi-container"
        />
      </div>
    )
  },
)

export default JitsiFrame
