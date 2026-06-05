/**
 * SkeletonLoader — reusable skeleton placeholder component for sidebar loading states.
 *
 * Renders animated placeholder rows that approximate the layout of content being loaded.
 * Supports two variants:
 * - 'participant': avatar circle + two text lines per row
 * - 'raise-hand': icon circle + single text line per row
 *
 * Uses a shimmer animation with slate-700 to slate-600 gradient on the dark sidebar background.
 *
 * Requirements: 14.1, 14.2, 14.3, 14.4
 */

// ─── Types ────────────────────────────────────────────────────────────────────

export interface SkeletonLoaderProps {
  /** Number of skeleton rows */
  rows: number
  /** Layout variant */
  variant: 'participant' | 'raise-hand'
}

// ─── Shimmer block ────────────────────────────────────────────────────────────

function ShimmerBlock({ className }: { className?: string }) {
  return (
    <div
      className={`animate-pulse rounded bg-gradient-to-r from-slate-700 to-slate-600 ${className ?? ''}`}
      aria-hidden="true"
    />
  )
}

// ─── Participant skeleton row ─────────────────────────────────────────────────

function ParticipantSkeletonRow() {
  return (
    <div className="flex items-center gap-3 px-3 py-2" data-testid="skeleton-row-participant">
      {/* Avatar circle */}
      <ShimmerBlock className="h-9 w-9 shrink-0 rounded-full" />
      {/* Two text lines */}
      <div className="flex flex-1 flex-col gap-1.5">
        <ShimmerBlock className="h-3 w-3/4 rounded" />
        <ShimmerBlock className="h-2.5 w-1/2 rounded" />
      </div>
    </div>
  )
}

// ─── Raise-hand skeleton row ──────────────────────────────────────────────────

function RaiseHandSkeletonRow() {
  return (
    <div className="flex items-center gap-3 px-3 py-2" data-testid="skeleton-row-raise-hand">
      {/* Icon circle */}
      <ShimmerBlock className="h-8 w-8 shrink-0 rounded-full" />
      {/* Single text line */}
      <ShimmerBlock className="h-3 w-2/3 rounded" />
    </div>
  )
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * SkeletonLoader
 *
 * Renders animated skeleton placeholder rows for sidebar loading states.
 * The 'participant' variant shows avatar + two text lines per row.
 * The 'raise-hand' variant shows icon circle + single text line per row.
 *
 * Requirements: 14.1, 14.2, 14.3, 14.4
 */
export default function SkeletonLoader({ rows, variant }: SkeletonLoaderProps) {
  const RowComponent = variant === 'participant' ? ParticipantSkeletonRow : RaiseHandSkeletonRow

  return (
    <div
      className="flex flex-col gap-1"
      data-testid={`skeleton-loader-${variant}`}
      role="status"
      aria-label="Loading content"
    >
      {Array.from({ length: rows }, (_, index) => (
        <RowComponent key={index} />
      ))}
    </div>
  )
}
