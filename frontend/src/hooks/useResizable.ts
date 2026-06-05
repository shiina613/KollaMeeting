/**
 * useResizable — encapsulates drag-resize logic for the sidebar.
 *
 * Handles mouse down/move/up events to allow the user to resize a panel
 * by dragging a handle. Uses the `clampWidth` utility to constrain the
 * resulting width within configured min/max bounds.
 *
 * Features:
 * - Tracks drag state (isDragging) for visual feedback
 * - Reports when the width has hit a limit (isAtLimit)
 * - Disables resize when `enabled` is false (tablet/mobile)
 * - Cleans up global mouse listeners on unmount or drag end
 *
 * Requirements: 1.3, 13.2, 15.1, 15.2, 15.3, 15.4
 */

import { useCallback, useEffect, useRef, useState } from 'react'
import { clampWidth } from '../utils/clampWidth'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseResizableOptions {
  /** Minimum allowed width in pixels */
  min: number
  /** Maximum allowed width in pixels */
  max: number
  /** Default/initial width in pixels */
  defaultWidth: number
  /** Whether resize is enabled (disabled on tablet/mobile) */
  enabled: boolean
}

export interface UseResizableReturn {
  /** Current width in pixels */
  width: number
  /** Whether the user is actively dragging the resize handle */
  isDragging: boolean
  /** Whether the width is at the min or max limit */
  isAtLimit: boolean
  /** Mouse down handler to attach to the resize handle element */
  handleMouseDown: (e: React.MouseEvent) => void
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useResizable
 *
 * Requirements: 1.3, 13.2, 15.1, 15.2, 15.3, 15.4
 */
export function useResizable(options: UseResizableOptions): UseResizableReturn {
  const { min, max, defaultWidth, enabled } = options

  const [width, setWidth] = useState(defaultWidth)
  const [isDragging, setIsDragging] = useState(false)

  const startXRef = useRef(0)
  const startWidthRef = useRef(defaultWidth)

  const isAtLimit = width <= min || width >= max

  const handleMouseMove = useCallback(
    (e: MouseEvent) => {
      const delta = e.clientX - startXRef.current
      const newWidth = clampWidth(startWidthRef.current, delta, min, max)
      setWidth(newWidth)
    },
    [min, max]
  )

  const handleMouseUp = useCallback(() => {
    setIsDragging(false)
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
  }, [handleMouseMove])

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      if (!enabled) return

      e.preventDefault()
      startXRef.current = e.clientX
      startWidthRef.current = width
      setIsDragging(true)

      document.addEventListener('mousemove', handleMouseMove)
      document.addEventListener('mouseup', handleMouseUp)
    },
    [enabled, width, handleMouseMove, handleMouseUp]
  )

  // Clean up listeners on unmount
  useEffect(() => {
    return () => {
      document.removeEventListener('mousemove', handleMouseMove)
      document.removeEventListener('mouseup', handleMouseUp)
    }
  }, [handleMouseMove, handleMouseUp])

  return {
    width,
    isDragging,
    isAtLimit,
    handleMouseDown,
  }
}
