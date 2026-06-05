// Feature: meeting-room-ux-improvements, Property 7: Sidebar resize clamping

/**
 * Property-based test for clampWidth utility.
 *
 * Property 7: Sidebar resize clamping
 * For any drag delta value (positive or negative, any magnitude) applied to the
 * sidebar resize handle, the resulting sidebar width SHALL be clamped between the
 * configured minimum (200px) and maximum (600px) values, inclusive.
 *
 * **Validates: Requirements 1.3, 13.2**
 */

import { describe, it } from 'vitest'
import * as fc from 'fast-check'
import { clampWidth } from '../clampWidth'

const MIN_WIDTH = 200
const MAX_WIDTH = 600

describe('Property 7: Sidebar resize clamping', () => {
  it('result is always >= min (200) for any delta and current width', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 800 }),
        fc.integer(),
        (current, delta) => {
          const result = clampWidth(current, delta, MIN_WIDTH, MAX_WIDTH)
          return result >= MIN_WIDTH
        },
      ),
      { numRuns: 200 },
    )
  })

  it('result is always <= max (600) for any delta and current width', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 800 }),
        fc.integer(),
        (current, delta) => {
          const result = clampWidth(current, delta, MIN_WIDTH, MAX_WIDTH)
          return result <= MAX_WIDTH
        },
      ),
      { numRuns: 200 },
    )
  })

  it('result is always within [min, max] inclusive for any inputs', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 800 }),
        fc.integer(),
        (current, delta) => {
          const result = clampWidth(current, delta, MIN_WIDTH, MAX_WIDTH)
          return result >= MIN_WIDTH && result <= MAX_WIDTH
        },
      ),
      { numRuns: 200 },
    )
  })

  it('result equals current + delta when within bounds', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 800 }),
        fc.integer(),
        (current, delta) => {
          const result = clampWidth(current, delta, MIN_WIDTH, MAX_WIDTH)
          const unclamped = current + delta
          if (unclamped >= MIN_WIDTH && unclamped <= MAX_WIDTH) {
            return result === unclamped
          }
          return true
        },
      ),
      { numRuns: 200 },
    )
  })

  it('result equals min when current + delta < min', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 800 }),
        fc.integer(),
        (current, delta) => {
          const result = clampWidth(current, delta, MIN_WIDTH, MAX_WIDTH)
          const unclamped = current + delta
          if (unclamped < MIN_WIDTH) {
            return result === MIN_WIDTH
          }
          return true
        },
      ),
      { numRuns: 200 },
    )
  })

  it('result equals max when current + delta > max', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 100, max: 800 }),
        fc.integer(),
        (current, delta) => {
          const result = clampWidth(current, delta, MIN_WIDTH, MAX_WIDTH)
          const unclamped = current + delta
          if (unclamped > MAX_WIDTH) {
            return result === MAX_WIDTH
          }
          return true
        },
      ),
      { numRuns: 200 },
    )
  })
})
