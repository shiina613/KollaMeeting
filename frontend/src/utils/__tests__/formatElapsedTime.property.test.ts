// Feature: meeting-room-ux-improvements, Property 1: Timer formatting correctness

/**
 * Property-based test for formatElapsedTime utility.
 *
 * Property 1: Timer formatting correctness
 * For any non-negative integer representing elapsed seconds (0 to 86400),
 * formatElapsedTime SHALL produce a string matching MM:SS when seconds < 3600,
 * or HH:MM:SS when seconds ≥ 3600, where the numeric values are mathematically
 * correct (hours = floor(s/3600), minutes = floor((s%3600)/60), seconds = s%60).
 *
 * **Validates: Requirements 4.1, 4.3, 4.4**
 */

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { formatElapsedTime } from '../formatElapsedTime'

describe('Property 1: Timer formatting correctness', () => {
  it('returns MM:SS format when input < 3600', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 3599 }),
        (totalSeconds) => {
          const result = formatElapsedTime(totalSeconds)
          // Must match MM:SS pattern (two digits colon two digits)
          return /^\d{2}:\d{2}$/.test(result)
        },
      ),
      { numRuns: 200 },
    )
  })

  it('returns HH:MM:SS format when input >= 3600', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 3600, max: 86400 }),
        (totalSeconds) => {
          const result = formatElapsedTime(totalSeconds)
          // Must match HH:MM:SS pattern (two digits colon two digits colon two digits)
          return /^\d{2}:\d{2}:\d{2}$/.test(result)
        },
      ),
      { numRuns: 200 },
    )
  })

  it('numeric values are mathematically correct for any input in [0, 86400]', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 86400 }),
        (totalSeconds) => {
          const result = formatElapsedTime(totalSeconds)

          const expectedHours = Math.floor(totalSeconds / 3600)
          const expectedMinutes = Math.floor((totalSeconds % 3600) / 60)
          const expectedSeconds = totalSeconds % 60

          const parts = result.split(':').map(Number)

          if (totalSeconds >= 3600) {
            // HH:MM:SS
            const [hours, minutes, seconds] = parts
            return (
              hours === expectedHours &&
              minutes === expectedMinutes &&
              seconds === expectedSeconds
            )
          } else {
            // MM:SS
            const [minutes, seconds] = parts
            return (
              minutes === expectedMinutes &&
              seconds === expectedSeconds
            )
          }
        },
      ),
      { numRuns: 200 },
    )
  })

  it('all segments are zero-padded to exactly 2 digits', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 86400 }),
        (totalSeconds) => {
          const result = formatElapsedTime(totalSeconds)
          const parts = result.split(':')
          return parts.every((part) => part.length === 2)
        },
      ),
      { numRuns: 200 },
    )
  })
})
