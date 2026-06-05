/**
 * clampWidth — clamps a width value after applying a delta to within [min, max] bounds.
 *
 * Pure function used by the useResizable hook to constrain sidebar width
 * during drag-resize operations.
 *
 * Requirements: 1.3, 13.2
 */

export function clampWidth(current: number, delta: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, current + delta))
}
