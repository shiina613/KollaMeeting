import { useEffect, useRef, useCallback } from 'react'
import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import useAuthStore from '../store/authStore'
import type { MeetingEvent } from '../types/meeting'
import type { Notification } from '../types/notification'

// ─── Constants ────────────────────────────────────────────────────────────────

const WS_URL = import.meta.env.VITE_WS_URL ?? 'http://localhost:8080/ws'

/** Exponential backoff delays in ms: 1s, 2s, 4s, 8s, 16s, 30s (capped) */
const BACKOFF_DELAYS = [1_000, 2_000, 4_000, 8_000, 16_000, 30_000]
const MAX_BACKOFF_MS = 30_000

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseWebSocketOptions {
  /** Meeting ID to subscribe to meeting-scoped events. Pass null to skip. */
  meetingId: number | null
  /** Called for every event on /topic/meeting/{meetingId} */
  onMeetingEvent?: (event: MeetingEvent) => void
  /** Called for every personal notification on /user/queue/notifications */
  onNotification?: (notification: Notification) => void
  /** Called when the STOMP connection is established */
  onConnected?: () => void
  /** Called when the STOMP connection is lost */
  onDisconnected?: () => void
}

export interface UseWebSocketReturn {
  /** Whether the STOMP client is currently connected */
  isConnected: boolean
  /** Manually disconnect and stop reconnect attempts */
  disconnect: () => void
  /** Manually trigger a reconnect */
  reconnect: () => void
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * Manages a STOMP-over-SockJS WebSocket connection to the backend.
 *
 * Features:
 * - Subscribes to `/topic/meeting/{meetingId}` for meeting-scoped broadcast events
 * - Subscribes to `/user/queue/notifications` for personal notifications
 * - Exponential backoff reconnect: 1s → 2s → 4s → 8s → 16s → 30s (max)
 * - Attaches JWT Bearer token on every connect attempt
 * - Cleans up subscriptions and client on unmount
 *
 * Requirements: 10.1
 */
export function useWebSocket({
  meetingId,
  onMeetingEvent,
  onNotification,
  onConnected,
  onDisconnected,
}: UseWebSocketOptions): UseWebSocketReturn {
  const clientRef = useRef<Client | null>(null)
  const isConnectedRef = useRef(false)
  const reconnectAttemptRef = useRef(0)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const isMountedRef = useRef(true)
  const subscriptionsRef = useRef<StompSubscription[]>([])

  // Keep callbacks in refs so the STOMP handlers always see the latest version
  // without needing to recreate the client on every render.
  const onMeetingEventRef = useRef(onMeetingEvent)
  const onNotificationRef = useRef(onNotification)
  const onConnectedRef = useRef(onConnected)
  const onDisconnectedRef = useRef(onDisconnected)
  const meetingIdRef = useRef(meetingId)

  useEffect(() => { onMeetingEventRef.current = onMeetingEvent }, [onMeetingEvent])
  useEffect(() => { onNotificationRef.current = onNotification }, [onNotification])
  useEffect(() => { onConnectedRef.current = onConnected }, [onConnected])
  useEffect(() => { onDisconnectedRef.current = onDisconnected }, [onDisconnected])
  useEffect(() => { meetingIdRef.current = meetingId }, [meetingId])

  // ── Helpers ────────────────────────────────────────────────────────────────

  const clearReconnectTimer = useCallback(() => {
    if (reconnectTimerRef.current !== null) {
      clearTimeout(reconnectTimerRef.current)
      reconnectTimerRef.current = null
    }
  }, [])

  const unsubscribeAll = useCallback(() => {
    subscriptionsRef.current.forEach((sub) => {
      try { sub.unsubscribe() } catch { /* ignore stale unsubscribe */ }
    })
    subscriptionsRef.current = []
  }, [])

  // ── Connect ────────────────────────────────────────────────────────────────

  const connect = useCallback(() => {
    if (!isMountedRef.current) return

    const token = useAuthStore.getState().token
    if (!token) return // Don't connect without a JWT

    const client = new Client({
      // SockJS factory — recreated on each connect attempt so the URL is fresh
      webSocketFactory: () => new SockJS(WS_URL) as WebSocket,

      // Attach JWT in STOMP CONNECT headers
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },

      // Disable @stomp/stompjs built-in reconnect — we handle it ourselves
      reconnectDelay: 0,

      onConnect: () => {
        if (!isMountedRef.current) return

        isConnectedRef.current = true
        reconnectAttemptRef.current = 0
        clearReconnectTimer()

        // Subscribe to personal notification queue
        const notifSub = client.subscribe(
          '/user/queue/notifications',
          (message: IMessage) => {
            try {
              const notification: Notification = JSON.parse(message.body)
              onNotificationRef.current?.(notification)
            } catch {
              console.error('[useWebSocket] Failed to parse notification:', message.body)
            }
          },
        )
        subscriptionsRef.current.push(notifSub)

        // Subscribe to meeting-scoped topic if meetingId is provided
        const currentMeetingId = meetingIdRef.current
        if (currentMeetingId !== null) {
          const meetingSub = client.subscribe(
            `/topic/meeting/${currentMeetingId}`,
            (message: IMessage) => {
              try {
                const event: MeetingEvent = JSON.parse(message.body)
                onMeetingEventRef.current?.(event)
              } catch {
                console.error('[useWebSocket] Failed to parse meeting event:', message.body)
              }
            },
          )
          subscriptionsRef.current.push(meetingSub)
        }

        onConnectedRef.current?.()
      },

      onDisconnect: () => {
        if (!isMountedRef.current) return
        isConnectedRef.current = false
        unsubscribeAll()
        onDisconnectedRef.current?.()
        scheduleReconnect()
      },

      onStompError: (frame) => {
        console.error('[useWebSocket] STOMP error:', frame.headers['message'], frame.body)
        if (!isMountedRef.current) return
        isConnectedRef.current = false
        unsubscribeAll()
        scheduleReconnect()
      },

      onWebSocketError: (event) => {
        console.error('[useWebSocket] WebSocket error:', event)
        if (!isMountedRef.current) return
        isConnectedRef.current = false
        unsubscribeAll()
        scheduleReconnect()
      },
    })

    clientRef.current = client
    client.activate()
  }, [clearReconnectTimer, unsubscribeAll]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── Reconnect with exponential backoff ─────────────────────────────────────

  const scheduleReconnect = useCallback(() => {
    if (!isMountedRef.current) return

    clearReconnectTimer()

    const attempt = reconnectAttemptRef.current
    const delay = attempt < BACKOFF_DELAYS.length
      ? BACKOFF_DELAYS[attempt]
      : MAX_BACKOFF_MS

    reconnectAttemptRef.current = attempt + 1

    reconnectTimerRef.current = setTimeout(() => {
      if (!isMountedRef.current) return
      // Deactivate stale client before creating a new one
      if (clientRef.current) {
        try { clientRef.current.deactivate() } catch { /* ignore */ }
        clientRef.current = null
      }
      connect()
    }, delay)
  }, [clearReconnectTimer, connect])

  // ── Public disconnect ──────────────────────────────────────────────────────

  const disconnect = useCallback(() => {
    isMountedRef.current = false // Prevent reconnect after manual disconnect
    clearReconnectTimer()
    unsubscribeAll()
    if (clientRef.current) {
      try { clientRef.current.deactivate() } catch { /* ignore */ }
      clientRef.current = null
    }
    isConnectedRef.current = false
  }, [clearReconnectTimer, unsubscribeAll])

  // ── Public reconnect ───────────────────────────────────────────────────────

  const reconnect = useCallback(() => {
    isMountedRef.current = true
    reconnectAttemptRef.current = 0
    clearReconnectTimer()
    unsubscribeAll()
    if (clientRef.current) {
      try { clientRef.current.deactivate() } catch { /* ignore */ }
      clientRef.current = null
    }
    connect()
  }, [clearReconnectTimer, unsubscribeAll, connect])

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  useEffect(() => {
    isMountedRef.current = true
    connect()

    return () => {
      isMountedRef.current = false
      clearReconnectTimer()
      unsubscribeAll()
      if (clientRef.current) {
        try { clientRef.current.deactivate() } catch { /* ignore */ }
        clientRef.current = null
      }
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps
  // Intentionally empty deps — we only want to connect once on mount.
  // meetingId changes are handled via meetingIdRef.

  return {
    isConnected: isConnectedRef.current,
    disconnect,
    reconnect,
  }
}

export default useWebSocket
