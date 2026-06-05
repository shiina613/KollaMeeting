// Feature: meeting-room-ux-improvements, Property 8: Connection quality level mapping
// Feature: meeting-room-ux-improvements, Property 9: Connection quality tooltip content

/**
 * Property-based tests for connection quality utilities.
 *
 * Property 8: Connection quality level mapping
 * For any combination of latency (0–10000ms) and packet loss (0–100%),
 * getQualityLevel SHALL return:
 * - 'good' when latency < 100 AND packetLoss === 0
 * - 'poor' when latency > 300 OR packetLoss > 5
 * - 'moderate' for all other valid combinations
 *
 * Property 9: Connection quality tooltip content
 * For any ConnectionStats object with numeric latency and packetLoss values,
 * the tooltip text SHALL contain both the latency value (in ms) and the
 * packet loss value (as percentage).
 *
 * **Validates: Requirements 16.2, 16.3, 16.4, 16.5**
 */

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { getQualityLevel, formatQualityTooltip } from '../connectionQuality'

const connectionStatsArb = fc.record({
  latency: fc.integer({ min: 0, max: 10000 }),
  packetLoss: fc.integer({ min: 0, max: 100 }),
})

describe('Property 8: Connection quality level mapping', () => {
  it('returns "good" when latency < 100 AND packetLoss === 0', () => {
    fc.assert(
      fc.property(
        fc.record({
          latency: fc.integer({ min: 0, max: 99 }),
          packetLoss: fc.constant(0),
        }),
        (stats) => {
          const level = getQualityLevel(stats)
          expect(level).toBe('good')
        },
      ),
      { numRuns: 200 },
    )
  })

  it('returns "poor" when latency > 300 OR packetLoss > 5', () => {
    fc.assert(
      fc.property(
        connectionStatsArb.filter(
          (stats) => stats.latency > 300 || stats.packetLoss > 5,
        ),
        (stats) => {
          const level = getQualityLevel(stats)
          expect(level).toBe('poor')
        },
      ),
      { numRuns: 200 },
    )
  })

  it('returns "moderate" for all other valid combinations', () => {
    fc.assert(
      fc.property(
        connectionStatsArb.filter(
          (stats) =>
            // Not good: NOT (latency < 100 AND packetLoss === 0)
            !(stats.latency < 100 && stats.packetLoss === 0) &&
            // Not poor: NOT (latency > 300 OR packetLoss > 5)
            !(stats.latency > 300 || stats.packetLoss > 5),
        ),
        (stats) => {
          const level = getQualityLevel(stats)
          expect(level).toBe('moderate')
        },
      ),
      { numRuns: 200 },
    )
  })

  it('returns "unavailable" when stats is null', () => {
    const level = getQualityLevel(null)
    expect(level).toBe('unavailable')
  })

  it('always returns one of the four valid quality levels for any stats', () => {
    fc.assert(
      fc.property(connectionStatsArb, (stats) => {
        const level = getQualityLevel(stats)
        expect(['good', 'moderate', 'poor', 'unavailable']).toContain(level)
      }),
      { numRuns: 200 },
    )
  })
})

describe('Property 9: Connection quality tooltip content', () => {
  it('tooltip contains the latency value in ms', () => {
    fc.assert(
      fc.property(connectionStatsArb, (stats) => {
        const tooltip = formatQualityTooltip(stats)
        expect(tooltip).toContain(`${stats.latency}ms`)
      }),
      { numRuns: 200 },
    )
  })

  it('tooltip contains the packet loss value as percentage', () => {
    fc.assert(
      fc.property(connectionStatsArb, (stats) => {
        const tooltip = formatQualityTooltip(stats)
        expect(tooltip).toContain(`${stats.packetLoss}%`)
      }),
      { numRuns: 200 },
    )
  })

  it('tooltip contains both latency and packetLoss values for any valid stats', () => {
    fc.assert(
      fc.property(connectionStatsArb, (stats) => {
        const tooltip = formatQualityTooltip(stats)
        expect(tooltip).toContain(`${stats.latency}`)
        expect(tooltip).toContain(`${stats.packetLoss}`)
      }),
      { numRuns: 200 },
    )
  })

  it('returns "No data" when stats is null', () => {
    const tooltip = formatQualityTooltip(null)
    expect(tooltip).toBe('No data')
  })
})
