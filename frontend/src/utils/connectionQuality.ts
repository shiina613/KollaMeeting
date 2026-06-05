/**
 * Connection quality utility functions for the meeting room.
 *
 * Provides pure functions to map connection stats (latency, packet loss)
 * to quality levels, bar counts, and tooltip text.
 *
 * Requirements: 16.2, 16.3, 16.4, 16.5
 */

export interface ConnectionStats {
  /** Round-trip latency in milliseconds */
  latency: number
  /** Packet loss percentage (0-100) */
  packetLoss: number
}

export type QualityLevel = 'good' | 'moderate' | 'poor' | 'unavailable'

/**
 * Maps connection stats to a quality level.
 *
 * - 'good': latency < 100ms AND packetLoss === 0
 * - 'moderate': latency 100–300ms OR packetLoss > 0 (but not poor)
 * - 'poor': latency > 300ms OR packetLoss > 5%
 * - 'unavailable': stats is null
 */
export function getQualityLevel(stats: ConnectionStats | null): QualityLevel {
  if (!stats) return 'unavailable'
  if (stats.latency > 300 || stats.packetLoss > 5) return 'poor'
  if (stats.latency >= 100 || stats.packetLoss > 0) return 'moderate'
  return 'good'
}

/**
 * Maps a quality level to the number of signal bars to display.
 *
 * - 'good' → 4 bars
 * - 'moderate' → 2 bars
 * - 'poor' → 1 bar
 * - 'unavailable' → 0 bars
 */
export function getBarCount(level: QualityLevel): number {
  switch (level) {
    case 'good':
      return 4
    case 'moderate':
      return 2
    case 'poor':
      return 1
    case 'unavailable':
      return 0
  }
}

/**
 * Formats connection stats into a human-readable tooltip string.
 *
 * Returns a string containing both latency (in ms) and packet loss (as %).
 * Returns "No data" when stats are null.
 */
export function formatQualityTooltip(stats: ConnectionStats | null): string {
  if (!stats) return 'No data'
  return `Latency: ${stats.latency}ms | Packet loss: ${stats.packetLoss}%`
}
