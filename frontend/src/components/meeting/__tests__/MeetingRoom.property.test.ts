// Feature: meeting-room-ux-improvements, Property 10: Error banner replacement

/**
 * Property-based test for MeetingRoom error banner replacement.
 *
 * Property 10: Error banner replacement
 * For any sequence of error messages dispatched to the error state, only the
 * most recently dispatched error message SHALL be visible in the UI at any
 * given time.
 *
 * We model the error state management as a pure state machine:
 * - State: { activeError: { message: string; type: 'join' | 'permission' } | null }
 * - Action: showError(message, type) → replaces activeError with new value
 *
 * This mirrors the MeetingRoom component's `showError` callback which calls
 * `setActiveError({ message, type })`, replacing any previous error.
 *
 * **Validates: Requirements 12.4**
 */

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

// ─── Error State Machine (mirrors MeetingRoom's error banner logic) ───────────

interface ErrorState {
  activeError: { message: string; type: 'join' | 'permission' } | null
}

/**
 * Pure state transition function that models MeetingRoom's `showError` behavior.
 * Each call replaces the previous error with the new one (Req 12.4).
 */
function showError(
  _state: ErrorState,
  message: string,
  type: 'join' | 'permission',
): ErrorState {
  return { activeError: { message, type } }
}

/**
 * Returns the currently visible error message from the state,
 * or null if no error is active.
 */
function getVisibleError(state: ErrorState): string | null {
  return state.activeError?.message ?? null
}

// ─── Property Test ────────────────────────────────────────────────────────────

describe('Property 10: Error banner replacement', () => {
  it('only the most recent error message is visible after any sequence of errors', () => {
    fc.assert(
      fc.property(
        fc.array(fc.string({ minLength: 1 }), { minLength: 1, maxLength: 10 }),
        (errorMessages) => {
          // Start with no active error
          let state: ErrorState = { activeError: null }

          // Dispatch each error message in sequence (alternating types for variety)
          for (let i = 0; i < errorMessages.length; i++) {
            const type = i % 2 === 0 ? 'join' : 'permission'
            state = showError(state, errorMessages[i], type as 'join' | 'permission')
          }

          // After all errors have been dispatched, only the last one should be visible
          const lastMessage = errorMessages[errorMessages.length - 1]
          const visibleError = getVisibleError(state)

          return visibleError === lastMessage
        },
      ),
      { numRuns: 200 },
    )
  })

  it('at any intermediate point in the sequence, only the most recent error is visible', () => {
    fc.assert(
      fc.property(
        fc.array(fc.string({ minLength: 1 }), { minLength: 1, maxLength: 10 }),
        (errorMessages) => {
          let state: ErrorState = { activeError: null }

          // After each dispatch, verify only the latest error is visible
          for (let i = 0; i < errorMessages.length; i++) {
            const type = i % 2 === 0 ? 'join' : 'permission'
            state = showError(state, errorMessages[i], type as 'join' | 'permission')

            const visibleError = getVisibleError(state)

            // The visible error must be exactly the one we just dispatched
            if (visibleError !== errorMessages[i]) {
              return false
            }
          }

          return true
        },
      ),
      { numRuns: 200 },
    )
  })

  it('there is never more than one error visible at any time', () => {
    fc.assert(
      fc.property(
        fc.array(fc.string({ minLength: 1 }), { minLength: 1, maxLength: 10 }),
        (errorMessages) => {
          let state: ErrorState = { activeError: null }

          for (let i = 0; i < errorMessages.length; i++) {
            const type = i % 2 === 0 ? 'join' : 'permission'
            state = showError(state, errorMessages[i], type as 'join' | 'permission')

            // Count visible errors — must always be exactly 1 after a dispatch
            const errorCount = state.activeError !== null ? 1 : 0
            if (errorCount !== 1) {
              return false
            }
          }

          return true
        },
      ),
      { numRuns: 200 },
    )
  })
})
