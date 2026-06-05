/**
 * ConnectionQualityIndicator — displays signal bars with tooltip showing
 * connection quality (latency and packet loss).
 *
 * Uses `getQualityLevel`, `getBarCount`, and `formatQualityTooltip` utilities
 * to map connection stats to a visual representation.
 *
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6
 */

import { useState } from 'react'
import {
  ConnectionStats,
  getQualityLevel,
  getBarCount,
  formatQualityTooltip,
  QualityLevel,
} from '../../utils/connectionQuality'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface ConnectionQualityIndicatorProps {
  /** Quality stats from Jitsi API, null if unavailable */
  stats: ConnectionStats | null
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const TOTAL_BARS = 4

const LEVEL_COLORS: Record<QualityLevel, string> = {
  good: 'bg-green-400',
  moderate: 'bg-amber-400',
  poor: 'bg-red-400',
  unavailable: 'bg-slate-500',
}

const INACTIVE_BAR_COLOR = 'bg-slate-600'

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * ConnectionQualityIndicator
 *
 * Renders 1–4 signal bars colored by quality level, with a tooltip on hover
 * showing latency and packet loss values. Shows neutral gray bars with
 * "No data" tooltip when stats are null.
 *
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6
 */
export default function ConnectionQualityIndicator({
  stats,
}: ConnectionQualityIndicatorProps) {
  const [showTooltip, setShowTooltip] = useState(false)

  const level = getQualityLevel(stats)
  const activeBars = getBarCount(level)
  const tooltipText = formatQualityTooltip(stats)

  return (
    <div
      className="relative inline-flex items-end gap-[2px]"
      data-testid="connection-quality-indicator"
      aria-label={`Connection quality: ${level}`}
      onMouseEnter={() => setShowTooltip(true)}
      onMouseLeave={() => setShowTooltip(false)}
    >
      {/* Signal bars */}
      {Array.from({ length: TOTAL_BARS }, (_, i) => {
        const barIndex = i + 1
        const isActive = barIndex <= activeBars
        const height = 4 + barIndex * 3 // 7px, 10px, 13px, 16px

        return (
          <span
            key={barIndex}
            className={`inline-block w-[3px] rounded-sm ${
              isActive ? LEVEL_COLORS[level] : INACTIVE_BAR_COLOR
            }`}
            style={{ height: `${height}px` }}
            data-testid={`signal-bar-${barIndex}`}
            data-active={isActive}
          />
        )
      })}

      {/* Tooltip */}
      {showTooltip && (
        <div
          className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 whitespace-nowrap rounded bg-slate-900 px-2 py-1 text-xs text-slate-200 shadow-lg"
          data-testid="connection-quality-tooltip"
          role="tooltip"
        >
          {tooltipText}
        </div>
      )}
    </div>
  )
}
