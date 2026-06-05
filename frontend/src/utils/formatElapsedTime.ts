/**
 * formatElapsedTime — formats elapsed seconds into a human-readable timer string.
 *
 * Returns `MM:SS` for durations under 1 hour, `HH:MM:SS` for 1 hour or more.
 * All segments are zero-padded to 2 digits.
 *
 * Requirements: 4.1, 4.3, 4.4
 */

export function formatElapsedTime(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60

  if (hours > 0) {
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
  }
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}
