// Feature: meeting-room-ux-improvements, Property 6: Focus trap containment

/**
 * Property-based test for useFocusManagement hook — focus trap containment.
 *
 * Property 6: Focus trap containment
 * For any sequence of Tab key presses while the sidebar is open as a mobile overlay,
 * keyboard focus SHALL remain within the sidebar container (never escaping to elements outside).
 *
 * **Validates: Requirements 8.3**
 */

import { describe, it, expect, afterEach } from 'vitest'
import * as fc from 'fast-check'
import { renderHook } from '@testing-library/react'
import { useFocusManagement, getFocusableElements } from '../useFocusManagement'
import type { UseFocusManagementOptions } from '../useFocusManagement'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function createButton(label: string): HTMLButtonElement {
  const button = document.createElement('button')
  button.textContent = label
  return button
}

function createContainer(...elements: HTMLElement[]): HTMLDivElement {
  const container = document.createElement('div')
  elements.forEach((el) => container.appendChild(el))
  document.body.appendChild(container)
  return container
}

function fireTabKey(shiftKey = false): void {
  const event = new KeyboardEvent('keydown', {
    key: 'Tab',
    shiftKey,
    bubbles: true,
    cancelable: true,
  })
  document.dispatchEvent(event)
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('Property 6: Focus trap containment', () => {
  let container: HTMLDivElement
  let outsideButton: HTMLButtonElement

  afterEach(() => {
    if (container && container.parentNode) {
      document.body.removeChild(container)
    }
    if (outsideButton && outsideButton.parentNode) {
      document.body.removeChild(outsideButton)
    }
  })

  it('focus remains within container after any number of Tab presses', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 50 }),
        (tabCount) => {
          // Setup: create container with multiple focusable elements
          const buttons = Array.from({ length: 5 }, (_, i) => createButton(`Button ${i}`))
          container = createContainer(...buttons)
          outsideButton = createButton('Outside')
          document.body.appendChild(outsideButton)

          const containerRef = { current: container }
          const returnFocusRef = { current: outsideButton }

          // Render hook with focus trap active (isOpen + isMobileOverlay)
          const { unmount } = renderHook(
            (props: UseFocusManagementOptions) => useFocusManagement(props),
            {
              initialProps: {
                isOpen: true,
                isMobileOverlay: true,
                containerRef,
                returnFocusRef,
              },
            },
          )

          // Start focus on the first element inside the container
          buttons[0].focus()

          const focusableElements = getFocusableElements(container)

          // Simulate tabCount Tab presses
          for (let i = 0; i < tabCount; i++) {
            fireTabKey(false)
          }

          // Assert: focus is still within the container
          const activeElement = document.activeElement
          const isContained = focusableElements.includes(activeElement as HTMLElement)
          expect(isContained).toBe(true)

          // Cleanup for next iteration
          unmount()
          document.body.removeChild(container)
          document.body.removeChild(outsideButton)
          container = null as unknown as HTMLDivElement
          outsideButton = null as unknown as HTMLButtonElement

          return isContained
        },
      ),
      { numRuns: 100 },
    )
  })

  it('focus remains within container after any number of Shift+Tab presses', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 50 }),
        (tabCount) => {
          // Setup: create container with multiple focusable elements
          const buttons = Array.from({ length: 5 }, (_, i) => createButton(`Button ${i}`))
          container = createContainer(...buttons)
          outsideButton = createButton('Outside')
          document.body.appendChild(outsideButton)

          const containerRef = { current: container }
          const returnFocusRef = { current: outsideButton }

          // Render hook with focus trap active (isOpen + isMobileOverlay)
          const { unmount } = renderHook(
            (props: UseFocusManagementOptions) => useFocusManagement(props),
            {
              initialProps: {
                isOpen: true,
                isMobileOverlay: true,
                containerRef,
                returnFocusRef,
              },
            },
          )

          // Start focus on the last element inside the container
          buttons[buttons.length - 1].focus()

          const focusableElements = getFocusableElements(container)

          // Simulate tabCount Shift+Tab presses
          for (let i = 0; i < tabCount; i++) {
            fireTabKey(true)
          }

          // Assert: focus is still within the container
          const activeElement = document.activeElement
          const isContained = focusableElements.includes(activeElement as HTMLElement)
          expect(isContained).toBe(true)

          // Cleanup for next iteration
          unmount()
          document.body.removeChild(container)
          document.body.removeChild(outsideButton)
          container = null as unknown as HTMLDivElement
          outsideButton = null as unknown as HTMLButtonElement

          return isContained
        },
      ),
      { numRuns: 100 },
    )
  })
})
