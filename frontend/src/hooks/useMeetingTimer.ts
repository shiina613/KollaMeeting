/**
 * useMeetingTimer — manages elapsed time calculation with 1-second interval.
 *
 * Accepts a `joinedAt` ISO timestamp string, calculates elapsed seconds
 * since that time, and formats the result using the `formatElapsedTime` utility.
 * Updates every second via `setInterval` and cleans up on unmount.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4
 */

import { useEffect, useState } from 'react'
import { formatElapsedTime } from '../utils/formatElapsedTime'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseMeetingTimerReturn {
  /** Formatted time string (MM:SS or HH:MM:SS) */
  formatted: string
  /** Raw elapsed seconds */
  elapsedSeconds: number
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function getElapsedSeconds(joinedAt: string): number {
  const joinedTime = new Date(joinedAt).getTime()
  const now = Date.now()
  const diffMs = now - joinedTime
  return Math.max(0, Math.floor(diffMs / 1000))
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useMeetingTimer
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4
 */
export function useMeetingTimer(joinedAt: string): UseMeetingTimerReturn {
  const [elapsedSeconds, setElapsedSeconds] = useState(() =>
    getElapsedSeconds(joinedAt)
  )

  useEffect(() => {
    // Recalculate immediately when joinedAt changes
    setElapsedSeconds(getElapsedSeconds(joinedAt))

    const intervalId = setInterval(() => {
      setElapsedSeconds(getElapsedSeconds(joinedAt))
    }, 1000)

    return () => {
      clearInterval(intervalId)
    }
  }, [joinedAt])

  return {
    formatted: formatElapsedTime(elapsedSeconds),
    elapsedSeconds,
  }
}
