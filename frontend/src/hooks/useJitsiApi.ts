/**
 * useJitsiApi — loads the Jitsi IFrame API script and manages the JitsiMeetExternalAPI instance.
 *
 * Features:
 * - Dynamically loads external_api.js from the Jitsi server
 * - Initialises the API with meetingCode, displayName, avatar, and JWT
 * - Exposes mute/unmute/muteAll controls
 * - Handles participantJoined, participantLeft, videoConferenceLeft events
 * - Cleans up the API instance on unmount
 *
 * Requirements: 4.1–4.7
 */

import { useEffect, useRef, useCallback, useState } from 'react'

// ─── Constants ────────────────────────────────────────────────────────────────

const JITSI_URL = import.meta.env.VITE_JITSI_URL ?? 'https://jitsi.kolla.local'
const JITSI_SCRIPT_ID = 'jitsi-external-api-script'

// ─── Types ────────────────────────────────────────────────────────────────────

/** Minimal typing for the Jitsi IFrame API — only the methods we use. */
export interface JitsiMeetExternalAPI {
  executeCommand(command: string, ...args: unknown[]): void
  addEventListener(event: string, listener: (data: unknown) => void): void
  removeEventListener(event: string, listener: (data: unknown) => void): void
  dispose(): void
  isAudioMuted(): Promise<boolean>
  getParticipantsInfo(): JitsiParticipantInfo[]
}

export interface JitsiParticipantInfo {
  participantId: string
  displayName: string
}

export interface JitsiParticipantEvent {
  id: string
  displayName?: string
}

export interface UseJitsiApiOptions {
  /** The container element to embed the Jitsi iframe into */
  containerRef: React.RefObject<HTMLDivElement | null>
  /** Meeting code used as the Jitsi room name */
  meetingCode: string
  /** Display name shown in the Jitsi UI */
  displayName: string
  /** Optional avatar URL */
  avatarUrl?: string
  /** JWT token for authenticated Jitsi rooms */
  jwt?: string
  /** Called when a participant joins the Jitsi conference */
  onParticipantJoined?: (event: JitsiParticipantEvent) => void
  /** Called when a participant leaves the Jitsi conference */
  onParticipantLeft?: (event: JitsiParticipantEvent) => void
  /** Called when the local user leaves the video conference */
  onVideoConferenceLeft?: () => void
}

export interface UseJitsiApiReturn {
  /** Whether the Jitsi API has been initialised and is ready */
  isReady: boolean
  /** Mute the local user's microphone */
  mute: () => void
  /** Unmute the local user's microphone */
  unmute: () => void
  /** Mute all participants (Host action) */
  muteAll: () => void
  /** Unmute a specific participant by their Jitsi participant ID */
  unmuteParticipant: (participantId: string) => void
  /** Direct access to the API instance (use sparingly) */
  apiRef: React.RefObject<JitsiMeetExternalAPI | null>
}

// ─── Script loader ────────────────────────────────────────────────────────────

/**
 * Loads the Jitsi external_api.js script once and resolves when ready.
 * Subsequent calls reuse the same promise.
 */
let scriptLoadPromise: Promise<void> | null = null

function loadJitsiScript(): Promise<void> {
  if (scriptLoadPromise) return scriptLoadPromise

  scriptLoadPromise = new Promise<void>((resolve, reject) => {
    // Already loaded
    if (typeof window !== 'undefined' && 'JitsiMeetExternalAPI' in window) {
      resolve()
      return
    }

    const existing = document.getElementById(JITSI_SCRIPT_ID)
    if (existing) {
      // Script tag exists but may still be loading — wait for it
      existing.addEventListener('load', () => resolve())
      existing.addEventListener('error', () => reject(new Error('Failed to load Jitsi script')))
      return
    }

    const script = document.createElement('script')
    script.id = JITSI_SCRIPT_ID
    script.src = `${JITSI_URL}/external_api.js`
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      scriptLoadPromise = null // Allow retry on next call
      reject(new Error(`Failed to load Jitsi IFrame API from ${JITSI_URL}/external_api.js`))
    }
    document.head.appendChild(script)
  })

  return scriptLoadPromise
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * Manages the Jitsi IFrame API lifecycle.
 *
 * Usage:
 * ```tsx
 * const containerRef = useRef<HTMLDivElement>(null)
 * const { isReady, mute, muteAll } = useJitsiApi({
 *   containerRef,
 *   meetingCode: 'ABC123',
 *   displayName: 'Nguyễn Văn A',
 *   jwt: token,
 *   onParticipantJoined: (e) => console.log('joined', e.id),
 * })
 * ```
 *
 * Requirements: 4.1–4.7
 */
export function useJitsiApi({
  containerRef,
  meetingCode,
  displayName,
  avatarUrl,
  jwt,
  onParticipantJoined,
  onParticipantLeft,
  onVideoConferenceLeft,
}: UseJitsiApiOptions): UseJitsiApiReturn {
  const apiRef = useRef<JitsiMeetExternalAPI | null>(null)
  const [isReady, setIsReady] = useState(false)
  const isMountedRef = useRef(true)

  // Keep callbacks in refs so event handlers always see the latest version
  const onParticipantJoinedRef = useRef(onParticipantJoined)
  const onParticipantLeftRef = useRef(onParticipantLeft)
  const onVideoConferenceLeftRef = useRef(onVideoConferenceLeft)

  useEffect(() => { onParticipantJoinedRef.current = onParticipantJoined }, [onParticipantJoined])
  useEffect(() => { onParticipantLeftRef.current = onParticipantLeft }, [onParticipantLeft])
  useEffect(() => { onVideoConferenceLeftRef.current = onVideoConferenceLeft }, [onVideoConferenceLeft])

  // ── Initialise Jitsi ───────────────────────────────────────────────────────

  useEffect(() => {
    isMountedRef.current = true

    if (!meetingCode || !containerRef.current) return

    let disposed = false

    loadJitsiScript()
      .then(() => {
        if (!isMountedRef.current || disposed) return
        if (!containerRef.current) return

        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        const JitsiAPI = (window as any).JitsiMeetExternalAPI
        if (!JitsiAPI) {
          console.error('[useJitsiApi] JitsiMeetExternalAPI not found on window after script load')
          return
        }

        const domain = JITSI_URL.replace(/^https?:\/\//, '')

        const options: Record<string, unknown> = {
          roomName: meetingCode,
          parentNode: containerRef.current,
          userInfo: {
            displayName,
            ...(avatarUrl ? { avatarUrl } : {}),
          },
          configOverwrite: {
            startWithAudioMuted: false,
            startWithVideoMuted: false,
            disableDeepLinking: true,
          },
          interfaceConfigOverwrite: {
            SHOW_JITSI_WATERMARK: false,
            SHOW_WATERMARK_FOR_GUESTS: false,
            TOOLBAR_BUTTONS: [
              'microphone', 'camera', 'closedcaptions', 'desktop',
              'fullscreen', 'fodeviceselection', 'hangup', 'chat',
              'settings', 'raisehand', 'videoquality', 'filmstrip',
              'tileview', 'help',
            ],
          },
        }

        if (jwt) {
          options.jwt = jwt
        }

        const api: JitsiMeetExternalAPI = new JitsiAPI(domain, options)
        apiRef.current = api

        // ── Event listeners ──────────────────────────────────────────────────

        api.addEventListener('participantJoined', (data: unknown) => {
          if (!isMountedRef.current) return
          const event = data as JitsiParticipantEvent
          onParticipantJoinedRef.current?.(event)
        })

        api.addEventListener('participantLeft', (data: unknown) => {
          if (!isMountedRef.current) return
          const event = data as JitsiParticipantEvent
          onParticipantLeftRef.current?.(event)
        })

        api.addEventListener('videoConferenceLeft', () => {
          if (!isMountedRef.current) return
          onVideoConferenceLeftRef.current?.()
        })

        if (isMountedRef.current) {
          setIsReady(true)
        }
      })
      .catch((err: Error) => {
        console.error('[useJitsiApi] Failed to initialise Jitsi:', err.message)
      })

    return () => {
      disposed = true
      isMountedRef.current = false
      if (apiRef.current) {
        try {
          apiRef.current.dispose()
        } catch {
          // Ignore errors during cleanup
        }
        apiRef.current = null
      }
      setIsReady(false)
    }
  }, [meetingCode]) // Re-initialise only when meetingCode changes
  // containerRef, displayName, avatarUrl, jwt are intentionally excluded —
  // Jitsi doesn't support hot-swapping these after init.

  // ── Controls ───────────────────────────────────────────────────────────────

  const mute = useCallback(() => {
    apiRef.current?.executeCommand('toggleAudio')
    // Ensure muted state — Jitsi toggleAudio is a toggle, so we check state
    apiRef.current?.isAudioMuted().then((muted) => {
      if (!muted) {
        apiRef.current?.executeCommand('toggleAudio')
      }
    }).catch(() => {
      // Fallback: just execute the command
      apiRef.current?.executeCommand('toggleAudio')
    })
  }, [])

  const unmute = useCallback(() => {
    apiRef.current?.isAudioMuted().then((muted) => {
      if (muted) {
        apiRef.current?.executeCommand('toggleAudio')
      }
    }).catch(() => {
      apiRef.current?.executeCommand('toggleAudio')
    })
  }, [])

  const muteAll = useCallback(() => {
    apiRef.current?.executeCommand('muteEveryone', 'audio')
  }, [])

  const unmuteParticipant = useCallback((_participantId: string) => {
    // Jitsi IFrame API does not support unmuting individual remote participants
    // from the host side — this is a Jitsi security restriction.
    // The participant must unmute themselves after receiving SPEAKING_PERMISSION_GRANTED.
    // We log this for clarity.
    console.info(
      '[useJitsiApi] unmuteParticipant: Jitsi does not allow remote unmute. ' +
      'The participant will be notified via WebSocket to unmute themselves.',
    )
  }, [])

  return {
    isReady,
    mute,
    unmute,
    muteAll,
    unmuteParticipant,
    apiRef,
  }
}

export default useJitsiApi
