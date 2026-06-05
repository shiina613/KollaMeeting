// Feature: meeting-room-ux-improvements, Property 2: Toast displays participant name for meeting events
// Feature: meeting-room-ux-improvements, Property 3: Toast queue maximum visibility

/**
 * Property-based tests for the toast store.
 *
 * Property 2: Toast displays participant name for meeting events
 * For any non-empty participant name string, when a raise-hand or
 * speaking-permission-granted event is processed by the toast system,
 * the resulting toast message SHALL contain that participant name as a substring.
 *
 * **Validates: Requirements 5.1, 5.2**
 *
 * Property 3: Toast queue maximum visibility
 * For any sequence of N toast additions (where N >= 1), the number of visible
 * toasts in the store SHALL never exceed 3 at any point in time.
 *
 * **Validates: Requirements 5.5**
 */

import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import * as fc from 'fast-check'
import useToastStore from '../toastStore'
import { createToastMessage } from '../toastStore'
import type { MeetingEvent } from '../../types/meeting'

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.useFakeTimers()
  useToastStore.getState().clearAll()
})

afterEach(() => {
  vi.useRealTimers()
})

// ─── Property 2: Toast displays participant name for meeting events ───────────

describe('Property 2: Toast displays participant name for meeting events', () => {
  it('RAISE_HAND toast message contains the participant name', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 100 }),
        (name) => {
          const event: MeetingEvent = {
            type: 'RAISE_HAND',
            meetingId: 1,
            timestamp: '2024-01-01T00:00:00Z',
            payload: { userId: 1, userName: name, requestedAt: '2024-01-01T00:00:00Z' },
          }

          const result = createToastMessage(event)

          expect(result).not.toBeNull()
          expect(result!.message).toContain(name)
        },
      ),
      { numRuns: 200 },
    )
  })

  it('SPEAKING_PERMISSION_GRANTED toast message contains the participant name', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 100 }),
        (name) => {
          const event: MeetingEvent = {
            type: 'SPEAKING_PERMISSION_GRANTED',
            meetingId: 1,
            timestamp: '2024-01-01T00:00:00Z',
            payload: { userId: 1, userName: name, speakerTurnId: 'turn-1' },
          }

          const result = createToastMessage(event)

          expect(result).not.toBeNull()
          expect(result!.message).toContain(name)
        },
      ),
      { numRuns: 200 },
    )
  })
})

// ─── Property 3: Toast queue maximum visibility ───────────────────────────────

describe('Property 3: Toast queue maximum visibility', () => {
  it('store never has more than 3 toasts after adding N toasts', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 20 }),
        (batchSize) => {
          // Reset store before each property iteration
          useToastStore.getState().clearAll()

          for (let i = 0; i < batchSize; i++) {
            useToastStore.getState().addToast({
              message: `Toast ${i}`,
              icon: '🔔',
              type: 'info',
            })
          }

          const { toasts } = useToastStore.getState()
          expect(toasts.length).toBeLessThanOrEqual(3)
        },
      ),
      { numRuns: 200 },
    )
  })
})
