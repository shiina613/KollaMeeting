/**
 * useJaasToken — manages JaaS token fetching, refresh scheduling, and error state.
 *
 * Extracts the JaaS token lifecycle from MeetingRoom into a reusable hook:
 * - Fetches a JaaS JWT token for the given meeting on mount
 * - Schedules automatic refresh at 55-minute intervals (token expires in 60 min)
 * - Exposes loading/error state and a manual retry function
 * - Cleans up the refresh timer on unmount
 *
 * Requirements: 13.3
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchJaasToken } from '../services/jaasService'

// ─── Constants ────────────────────────────────────────────────────────────────

/** Refresh interval: 55 minutes in milliseconds */
const TOKEN_REFRESH_INTERVAL_MS = 55 * 60 * 1000

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Whether JaaS is configured (app ID present in env) */
function isJaasEnabled(): boolean {
  return (import.meta.env.VITE_JAAS_APP_ID ?? '').length > 0
}

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseJaasTokenReturn {
  /** The current JaaS JWT token, or null if not yet fetched / JaaS disabled */
  token: string | null
  /** The JaaS room name, or null if not yet fetched / JaaS disabled */
  roomName: string | null
  /** Whether the token is currently being fetched */
  isLoading: boolean
  /** Error message if the token fetch failed, or null */
  error: string | null
  /** Manually retry fetching the token (e.g. after an error) */
  retry: () => void
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useJaasToken
 *
 * Manages JaaS token fetching and automatic refresh for a given meeting.
 * If JaaS is not configured (VITE_JAAS_APP_ID is empty), the hook returns
 * immediately with null token/roomName and isLoading=false.
 *
 * Requirements: 13.3
 */
export function useJaasToken(meetingId: number): UseJaasTokenReturn {
  const jaasEnabled = isJaasEnabled()

  const [token, setToken] = useState<string | null>(null)
  const [roomName, setRoomName] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState<boolean>(jaasEnabled)
  const [error, setError] = useState<string | null>(null)

  const tokenRefreshTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const fetchTokenInternal = useCallback(async () => {
    setIsLoading(true)
    setError(null)
    try {
      const { token: newToken, roomName: newRoomName } = await fetchJaasToken(meetingId)
      setToken(newToken)
      setRoomName(newRoomName)
      // Schedule refresh at 55-minute mark (token expires in 60 min)
      tokenRefreshTimerRef.current = setTimeout(fetchTokenInternal, TOKEN_REFRESH_INTERVAL_MS)
    } catch (_err) {
      setError('Không thể lấy token JaaS. Vui lòng thử lại.')
    } finally {
      setIsLoading(false)
    }
  }, [meetingId])

  // Fetch token on mount (only if JaaS is configured)
  useEffect(() => {
    if (!jaasEnabled) return

    fetchTokenInternal()

    return () => {
      if (tokenRefreshTimerRef.current) {
        clearTimeout(tokenRefreshTimerRef.current)
        tokenRefreshTimerRef.current = null
      }
    }
  }, [fetchTokenInternal, jaasEnabled])

  const retry = useCallback(() => {
    // Clear any existing refresh timer before retrying
    if (tokenRefreshTimerRef.current) {
      clearTimeout(tokenRefreshTimerRef.current)
      tokenRefreshTimerRef.current = null
    }
    fetchTokenInternal()
  }, [fetchTokenInternal])

  return {
    token,
    roomName,
    isLoading,
    error,
    retry,
  }
}
