/**
 * useConnectionQuality — polls Jitsi External API for connection stats.
 *
 * Accepts a ref to the JitsiFrame handle and an optional polling interval
 * (default 5000ms). On each poll, attempts to retrieve connection stats
 * from the Jitsi iframe API. Uses the `getQualityLevel` utility to map
 * raw stats to a quality level.
 *
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.6
 */

import { useEffect, useRef, useState } from 'react'
import type { JitsiFrameHandle } from '../components/meeting/JitsiFrame'
import {
  getQualityLevel,
  type ConnectionStats,
  type QualityLevel,
} from '../utils/connectionQuality'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseConnectionQualityOptions {
  /** Ref to the JitsiFrame imperative handle */
  jitsiRef: React.RefObject<JitsiFrameHandle | null>
  /** Polling interval in ms (default: 5000) */
  interval?: number
}

export interface UseConnectionQualityReturn {
  /** Current connection stats, null if unavailable */
  stats: ConnectionStats | null
  /** Quality level derived from stats */
  level: QualityLevel
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useConnectionQuality
 *
 * Polls the Jitsi External API for connection quality statistics at a
 * configurable interval. Returns the raw stats and a computed quality level.
 *
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.6
 */
export function useConnectionQuality(
  options: UseConnectionQualityOptions
): UseConnectionQualityReturn {
  const { jitsiRef, interval = 5000 } = options

  const [stats, setStats] = useState<ConnectionStats | null>(null)
  const [level, setLevel] = useState<QualityLevel>('unavailable')
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    function pollConnectionStats() {
      const handle = jitsiRef.current

      // If the Jitsi frame isn't ready or ref is null, mark as unavailable
      if (!handle || !handle.isReady) {
        setStats(null)
        setLevel('unavailable')
        return
      }

      // Attempt to get connection stats from the Jitsi API.
      // The JitsiFrame exposes the underlying API via the handle.
      // If getConnectionStats is available, use it; otherwise mark unavailable.
      const api = (handle as unknown as { _api?: JitsiExternalAPI })._api
      if (api && typeof api.getConnectionQuality === 'function') {
        try {
          const quality = api.getConnectionQuality()
          if (quality && typeof quality.local === 'object') {
            const local = quality.local
            const newStats: ConnectionStats = {
              latency: typeof local.jitterBufferDelay === 'number'
                ? local.jitterBufferDelay
                : typeof local.roundTripTime === 'number'
                  ? local.roundTripTime
                  : 0,
              packetLoss: typeof local.packetLoss === 'number'
                ? local.packetLoss
                : 0,
            }
            setStats(newStats)
            setLevel(getQualityLevel(newStats))
            return
          }
        } catch {
          // Fall through to unavailable
        }
      }

      // Fallback: stats not available from the API
      setStats(null)
      setLevel('unavailable')
    }

    // Poll immediately on mount/change
    pollConnectionStats()

    // Set up polling interval
    intervalRef.current = setInterval(pollConnectionStats, interval)

    return () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
  }, [jitsiRef, interval])

  return { stats, level }
}

// ─── Internal Types ───────────────────────────────────────────────────────────

/** Minimal typing for the Jitsi External API connection quality methods */
interface JitsiExternalAPI {
  getConnectionQuality?: () => {
    local?: {
      jitterBufferDelay?: number
      roundTripTime?: number
      packetLoss?: number
    }
  }
}
