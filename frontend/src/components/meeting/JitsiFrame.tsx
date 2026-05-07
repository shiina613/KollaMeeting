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

const JAAS_APP_ID = import.meta.env.VITE_JAAS_APP_ID ?? ''
const IS_JAAS = JAAS_APP_ID.length > 0

// Domain: 8x8.vc if JaaS enabled, otherwise keep JITSI_DOMAIN
const EFFECTIVE_DOMAIN = IS_JAAS ? '8x8.vc' : JITSI_DOMAIN

// Script URL: load from the configured domain
const SCRIPT_SRC = IS_JAAS
  ? 'https://8x8.vc/external_api.js'
  : `${JITSI_URL}/external_api.js`

// ─── Types ────────────────────────────────────────────────────────────────────

export interface JitsiFrameHandle {
  mute: () => void
  unmute: () => void
  muteLocal: () => void
  unmuteLocal: () => void
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
  /** Fired when local user's mic mute state changes. `isMuted=false` = mic is ON. */
  onAudioMuteStatusChanged?: (isMuted: boolean) => void
  className?: string
}

// Extend window for JitsiMeetExternalAPI
interface JitsiAPI {
  executeCommand: (cmd: string, ...args: unknown[]) => void
  dispose: () => void
  addEventListeners: (listeners: Record<string, (e: unknown) => void>) => void
  isAudioMuted: () => Promise<boolean>
}

declare global {
  interface Window {
    JitsiMeetExternalAPI?: new (domain: string, options: object) => JitsiAPI
  }
}

// ─── Component ────────────────────────────────────────────────────────────────

const JitsiFrame = forwardRef<JitsiFrameHandle, JitsiFrameProps>(
  function JitsiFrame({ meetingCode, displayName, jwt, onVideoConferenceLeft, onAudioMuteStatusChanged, className = '' }, ref) {
    const containerRef = useRef<HTMLDivElement>(null)
    const apiRef = useRef<JitsiAPI | null>(null)
    const [isReady, setIsReady] = useState(false)
    const [scriptError, setScriptError] = useState(false)
    const onAudioMuteStatusChangedRef = useRef(onAudioMuteStatusChanged)

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
      script.src = SCRIPT_SRC
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
          // Always start muted — meeting mode and permission system control when mic is on.
          startWithAudioMuted: true,
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

      const api = new window.JitsiMeetExternalAPI(EFFECTIVE_DOMAIN, options)
      apiRef.current = api

      api.addEventListeners({
        videoConferenceJoined: () => setIsReady(true),
        videoConferenceLeft: () => onVideoConferenceLeft?.(),
        audioMuteStatusChanged: (data: unknown) => {
          const { muted } = data as { muted: boolean }
          // Use ref so we always call the latest callback without re-initialising Jitsi
          onAudioMuteStatusChangedRef.current?.(muted)
        },
      })

      // Fallback: hide loading overlay after 8s regardless of event
      const fallbackTimer = setTimeout(() => setIsReady(true), 8_000)

      return () => {
        clearTimeout(fallbackTimer)
        try { api.dispose() } catch { /* ignore */ }
        apiRef.current = null
      }
    }, [scriptLoaded, meetingCode, displayName, jwt, onVideoConferenceLeft])

    useImperativeHandle(ref, () => ({
      mute: () => apiRef.current?.executeCommand('toggleAudio'),
      unmute: () => apiRef.current?.executeCommand('toggleAudio'),
      muteLocal: () => {
        // Only toggle if currently unmuted — prevents accidental unmuting
        apiRef.current?.isAudioMuted().then((muted: boolean) => {
          if (!muted) {
            apiRef.current?.executeCommand('toggleAudio')
          }
        }).catch(() => { /* ignore */ })
      },
      unmuteLocal: () => {
        // Only toggle if currently muted — prevents accidental muting
        apiRef.current?.isAudioMuted().then((muted: boolean) => {
          if (muted) {
            apiRef.current?.executeCommand('toggleAudio')
          }
        }).catch(() => { /* ignore */ })
      },
      muteAll: () => {
        // muteEveryone only mutes remote participants, so also mute self
        apiRef.current?.executeCommand('muteEveryone', 'audio')
        // Mute local user as well
        apiRef.current?.isAudioMuted().then((muted: boolean) => {
          if (!muted) {
            apiRef.current?.executeCommand('toggleAudio')
          }
        }).catch(() => { /* ignore */ })
      },
      isReady,
    }), [isReady])

    if (scriptError) {
      return (
        <div className={`relative w-full h-full flex flex-col items-center justify-center bg-slate-900 text-white gap-4 ${className}`}>
          <span className="material-symbols-outlined text-5xl text-slate-400">videocam_off</span>
          <p className="text-body-sm text-slate-300">Không thể tải Jitsi Meet.</p>
          <p className="text-label-md text-slate-500">Kiểm tra kết nối đến {IS_JAAS ? EFFECTIVE_DOMAIN : JITSI_URL}</p>
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
