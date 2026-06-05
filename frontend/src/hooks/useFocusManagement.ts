/**
 * useFocusManagement — manages focus movement and trapping for the sidebar.
 *
 * Handles focus transitions when the sidebar opens/closes, implements a focus
 * trap for mobile overlay mode, and handles Escape key to close the overlay.
 *
 * Features:
 * - Moves focus to first focusable element when sidebar opens
 * - Returns focus to toggle button when sidebar closes
 * - Traps focus within container in mobile overlay mode (Tab/Shift+Tab wraps)
 * - Handles Escape key to close overlay
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */

import { useEffect, useRef } from 'react'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseFocusManagementOptions {
  /** Whether the sidebar is currently open */
  isOpen: boolean
  /** Whether the sidebar is rendered as a mobile overlay */
  isMobileOverlay: boolean
  /** Ref to the sidebar container element */
  containerRef: React.RefObject<HTMLDivElement>
  /** Ref to the element that should receive focus when sidebar closes */
  returnFocusRef: React.RefObject<HTMLElement>
  /** Callback to close the overlay (triggered by Escape key) */
  onClose?: () => void
}

// ─── Constants ────────────────────────────────────────────────────────────────

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'textarea:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(', ')

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Returns all focusable elements within a container.
 */
export function getFocusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useFocusManagement
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */
export function useFocusManagement(options: UseFocusManagementOptions): void {
  const { isOpen, isMobileOverlay, containerRef, returnFocusRef, onClose } = options

  const previouslyOpenRef = useRef(false)

  // Move focus when sidebar opens, return focus when it closes
  useEffect(() => {
    const wasOpen = previouslyOpenRef.current
    previouslyOpenRef.current = isOpen

    if (!wasOpen && isOpen) {
      // Sidebar just opened — move focus to first focusable element
      // Use requestAnimationFrame to ensure the DOM has rendered
      requestAnimationFrame(() => {
        const container = containerRef.current
        if (!container) return

        const focusableElements = getFocusableElements(container)
        if (focusableElements.length > 0) {
          focusableElements[0].focus()
        } else {
          // If no focusable elements, focus the container itself
          container.setAttribute('tabindex', '-1')
          container.focus()
        }
      })
    } else if (wasOpen && !isOpen) {
      // Sidebar just closed — return focus to toggle button
      requestAnimationFrame(() => {
        const returnElement = returnFocusRef.current
        if (returnElement) {
          returnElement.focus()
        }
      })
    }
  }, [isOpen, containerRef, returnFocusRef])

  // Focus trap for mobile overlay mode
  useEffect(() => {
    if (!isOpen || !isMobileOverlay) return

    const container = containerRef.current
    if (!container) return

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose?.()
        return
      }

      if (event.key !== 'Tab') return

      const focusableElements = getFocusableElements(container!)
      if (focusableElements.length === 0) return

      const firstElement = focusableElements[0]
      const lastElement = focusableElements[focusableElements.length - 1]

      if (event.shiftKey) {
        // Shift+Tab: if focus is on first element, wrap to last
        if (document.activeElement === firstElement) {
          event.preventDefault()
          lastElement.focus()
        }
      } else {
        // Tab: if focus is on last element, wrap to first
        if (document.activeElement === lastElement) {
          event.preventDefault()
          firstElement.focus()
        }
      }
    }

    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen, isMobileOverlay, containerRef, onClose])
}
