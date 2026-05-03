/**
 * JitsiFrame — embeds Jitsi Meet via the External API (external_api.js).
 *
 * Uses the official Jitsi IFrame API loaded from the Jitsi server.
 * This is the only reliable way to embed meet.jit.si — direct iframe
 * embedding is blocked by X-Frame-Options on the public instance.
 *
 * Requirements: 4.7
 */

import { forwardRef, useImperativeHandle, useEffect, useRef, useState } from 'react'
import type { JitsiParticipantEvent } from '../../hooks/useJitsiApi'

const JITSI_URL = import.meta.env.VITE_JITSI_URL ?? 'https://meet.jit.si'
// Extract hostname for the API (e.g. "meet.jit.si")
const JITSI_DOMAIN = JITSI_URL.replace(/^https?:\/\//, '').replace(/\/$/, '')

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

// Extend window for JitsiMeetExternalAPI
declare global {
  interface Window {
    JitsiMeetExternalAPI?: new (domain: string, options: object) => {
      executeCommand: (cmd: string, ...args: unknown[]) => void
      dispose: () => void
      addEventListeners: (listeners: Record<string, (e: unknown) => void>) => void
    }
  }
}

// ─── Component ────────────────────────────────────────────────────────────────

const JitsiFrame = forwardRef<JitsiFrameHandle, JitsiFrameProps>(
  function JitsiFrame({ meetingCode, displayName, jwt, onVideoConferenceLeft, className = '' }, ref) {
    const containerRef = useRef<HTMLDivElement>(null)
    const apiRef = useRef<ReturnType<NonNullable<typeof window.JitsiMeetExternalAPI>> | null>(null)
    const [isReady, setIsReady] = useState(false)
    const [scriptError, setScriptError] = useState(false)

    const [scriptLoaded, setScriptLoaded] = useState(false)

    // Load external_api.js once
    useEffect(() => {
      const scriptId = 'jitsi-external-api'
      const existing = document.getElementById(scriptId) as HTMLScriptElement | null

      if (existing) {
        if (window.JitsiMeetExternalAPI) {
          setScriptLoaded(true)
        } else {
          const onLoad = () => setScriptLoaded(true)
          const onError = () => setScriptError(true)
          existing.addEventListener('load', onLoad)
          existing.addEventListener('error', onError)
          return () => {
            existing.removeEventListener('load', onLoad)
            existing.removeEventListener('error', onError)
          }
        }
        return
      }

      const script = document.createElement('script')
      script.id = scriptId
      script.src = `${JITSI_URL}/external_api.js`
      script.async = true
      script.onload = () => setScriptLoaded(true)
      script.onerror = () => setScriptError(true)
      document.head.appendChild(script)
    }, [])

    // Init Jitsi once script is loaded AND container is mounted
    useEffect(() => {
      if (!scriptLoaded) return
      if (!containerRef.current) return
      if (!window.JitsiMeetExternalAPI) return

      // Dispose previous instance if any
      if (apiRef.current) {
        try { apiRef.current.dispose() } catch { /* ignore */ }
        apiRef.current = null
      }

      const options: Record<string, unknown> = {
        roomName: meetingCode,
        parentNode: containerRef.current,
        width: '100%',
        height: '100%',
        userInfo: { displayName },
        configOverwrite: {
          startWithAudioMuted: false,
          disableDeepLinking: true,
          prejoinPageEnabled: false,
          disableInviteFunctions: true,
        },
        interfaceConfigOverwrite: {
          SHOW_JITSI_WATERMARK: false,
          SHOW_WATERMARK_FOR_GUESTS: false,
          TOOLBAR_BUTTONS: [
            'microphone', 'camera', 'closedcaptions', 'desktop',
            'fullscreen', 'fodeviceselection', 'hangup', 'chat',
            'raisehand', 'videoquality', 'filmstrip', 'tileview',
          ],
        },
      }

      if (jwt) options.jwt = jwt

      const api = new window.JitsiMeetExternalAPI(JITSI_DOMAIN, options)
      apiRef.current = api

      api.addEventListeners({
        videoConferenceJoined: () => setIsReady(true),
        videoConferenceLeft: () => onVideoConferenceLeft?.(),
      })

      // Fallback: hide loading overlay after 8s regardless of event
      const fallbackTimer = setTimeout(() => setIsReady(true), 8_000)

      return () => {
        clearTimeout(fallbackTimer)
        try { api.dispose() } catch { /* ignore */ }
        apiRef.current = null
      }
    }, [scriptLoaded, meetingCode, displayName, jwt, onVideoConferenceLeft])

    // Expose controls
    useImperativeHandle(ref, () => ({
      mute: () => apiRef.current?.executeCommand('toggleAudio'),
      unmute: () => apiRef.current?.executeCommand('toggleAudio'),
      muteAll: () => apiRef.current?.executeCommand('muteEveryone', 'audio'),
      isReady,
    }), [isReady])

    if (scriptError) {
      return (
        <div className={`relative w-full h-full flex flex-col items-center justify-center bg-slate-900 text-white gap-4 ${className}`}>
          <span className="material-symbols-outlined text-5xl text-slate-400">videocam_off</span>
          <p className="text-body-sm text-slate-300">Không thể tải Jitsi Meet.</p>
          <p className="text-label-md text-slate-500">Kiểm tra kết nối đến {JITSI_URL}</p>
        </div>
      )
    }

    return (
      <div className={`relative w-full h-full ${className}`}>
        {/* Loading overlay */}
        {!isReady && (
          <div
            className="absolute inset-0 flex flex-col items-center justify-center bg-slate-900 text-white z-10"
            aria-live="polite"
          >
            <div className="w-10 h-10 border-4 border-white border-t-transparent rounded-full animate-spin mb-4" />
            <p className="text-body-sm text-slate-300">Đang kết nối phòng họp...</p>
          </div>
        )}
        {/* Jitsi mounts here */}
        <div
          ref={containerRef}
          className="w-full h-full"
          data-testid="jitsi-container"
        />
      </div>
    )
  },
)

export default JitsiFrame
