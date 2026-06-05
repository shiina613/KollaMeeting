// Feature: meeting-room-ux-improvements, Property 5: Keyboard shortcuts suppressed on input focus

/**
 * Property-based test for shouldSuppressShortcut utility.
 *
 * Property 5: Keyboard shortcuts suppressed on input focus
 * For any registered keyboard shortcut, when the active element is an `<input>`,
 * `<textarea>`, or element with `contenteditable="true"`, the shortcut action
 * SHALL NOT be invoked (shouldSuppressShortcut returns true).
 * For other elements (e.g., `<button>`, `<div>`), it returns false.
 *
 * **Validates: Requirements 7.4**
 */

import { describe, it, afterEach } from 'vitest'
import * as fc from 'fast-check'
import { shouldSuppressShortcut } from '../shouldSuppressShortcut'

describe('Property 5: Keyboard shortcuts suppressed on input focus', () => {
  afterEach(() => {
    // Reset focus to body after each test
    if (document.activeElement && document.activeElement !== document.body) {
      ;(document.activeElement as HTMLElement).blur()
    }
  })

  it('returns true for input, textarea, and contenteditable elements; false otherwise', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('input', 'textarea', 'div[contenteditable]', 'button', 'div'),
        (elementDescriptor) => {
          // Create and focus the appropriate element
          let element: HTMLElement

          if (elementDescriptor === 'div[contenteditable]') {
            element = document.createElement('div')
            element.setAttribute('contenteditable', 'true')
          } else {
            element = document.createElement(elementDescriptor)
          }

          document.body.appendChild(element)
          element.focus()

          const result = shouldSuppressShortcut()

          // Clean up
          document.body.removeChild(element)

          // Assert: input, textarea, and contenteditable should suppress; others should not
          const shouldSuppress =
            elementDescriptor === 'input' ||
            elementDescriptor === 'textarea' ||
            elementDescriptor === 'div[contenteditable]'

          return result === shouldSuppress
        },
      ),
      { numRuns: 100 },
    )
  })

  it('returns true when active element is an input', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('input', 'textarea'),
        (tagName) => {
          const element = document.createElement(tagName)
          document.body.appendChild(element)
          element.focus()

          const result = shouldSuppressShortcut()

          document.body.removeChild(element)

          return result === true
        },
      ),
      { numRuns: 100 },
    )
  })

  it('returns true when active element has contenteditable="true"', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('div', 'span', 'p'),
        (tagName) => {
          const element = document.createElement(tagName)
          element.setAttribute('contenteditable', 'true')
          document.body.appendChild(element)
          element.focus()

          const result = shouldSuppressShortcut()

          document.body.removeChild(element)

          return result === true
        },
      ),
      { numRuns: 100 },
    )
  })

  it('returns false when active element is a non-input element without contenteditable', () => {
    fc.assert(
      fc.property(
        fc.constantFrom('button', 'div', 'span', 'a', 'section'),
        (tagName) => {
          const element = document.createElement(tagName)
          // Ensure it's focusable by adding tabindex
          element.setAttribute('tabindex', '0')
          document.body.appendChild(element)
          element.focus()

          const result = shouldSuppressShortcut()

          document.body.removeChild(element)

          return result === false
        },
      ),
      { numRuns: 100 },
    )
  })
})
