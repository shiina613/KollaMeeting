/**
 * Property-based tests for audioUtils.ts using fast-check.
 *
 * Property 7: Minutes Assembly Ordering (frontend display)
 *   - For any collection of transcription segments, sortSegments produces a
 *     result where segments within the same speakerTurnId are in ascending
 *     sequenceNumber order, and turns are ordered by their earliest
 *     segmentStartTime.
 *   Validates: Requirements 8.12
 *
 * Property 5: Adaptive VAD Threshold Function (frontend mirror)
 *   - For any non-negative accumulated duration, adaptiveVadThreshold returns
 *     a value within the range defined by vadThresholdRange.
 *   - The threshold is strictly lower for long turns (>= 15s) than for short
 *     turns (< 15s), ensuring faster chunking as audio accumulates.
 *   Validates: Requirements 8.9
 *
 * Requirements: 20.3
 */

import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  float32ToInt16,
  sortSegments,
  adaptiveVadThreshold,
  vadThresholdRange,
  type TranscriptionSegment,
} from '../audioUtils'

// ─── Arbitraries ──────────────────────────────────────────────────────────────

/** Generate a valid ISO 8601 timestamp string (UTC+7). */
const isoTimestampArb = fc.date({
  min: new Date('2020-01-01T00:00:00Z'),
  max: new Date('2030-12-31T23:59:59Z'),
}).map((d) => d.toISOString())

/** Generate a single TranscriptionSegment with controlled fields. */
const segmentArb = (
  speakerTurnId: string,
  sequenceNumber: number,
  segmentStartTime: string,
): fc.Arbitrary<TranscriptionSegment> =>
  fc.record({
    jobId: fc.constant(`job-${speakerTurnId}-${sequenceNumber}`),
    speakerId: fc.integer({ min: 1, max: 100 }),
    speakerName: fc.string({ minLength: 1, maxLength: 50 }),
    speakerTurnId: fc.constant(speakerTurnId),
    sequenceNumber: fc.constant(sequenceNumber),
    text: fc.string({ minLength: 1, maxLength: 200 }),
    confidence: fc.option(fc.float({ min: 0, max: 1 }), { nil: null }),
    segmentStartTime: fc.constant(segmentStartTime),
  })

/**
 * Generate a list of segments for a single speaker turn.
 * Sequence numbers are 1..N (unique, ascending).
 */
const singleTurnSegmentsArb = fc
  .tuple(
    fc.string({ minLength: 1, maxLength: 36 }).filter((s) => s.trim().length > 0),
    isoTimestampArb,
    fc.integer({ min: 1, max: 10 }),
  )
  .chain(([turnId, startTime, count]) =>
    fc.tuple(
      ...Array.from({ length: count }, (_, i) =>
        segmentArb(turnId, i + 1, startTime),
      ),
    ).map((segs) => segs as TranscriptionSegment[]),
  )

/**
 * Generate segments for multiple distinct speaker turns.
 * Each turn has a unique ID and a distinct start time.
 */
const multiTurnSegmentsArb = fc
  .array(
    fc.tuple(
      fc.uuid(),
      isoTimestampArb,
      fc.integer({ min: 1, max: 5 }),
    ),
    { minLength: 1, maxLength: 5 },
  )
  .chain((turns) => {
    // Ensure unique turn IDs
    const uniqueTurns = Array.from(
      new Map(turns.map((t) => [t[0], t])).values(),
    )
    return fc.tuple(
      ...uniqueTurns.flatMap(([turnId, startTime, count]) =>
        Array.from({ length: count }, (_, i) =>
          segmentArb(turnId, i + 1, startTime),
        ),
      ),
    ).map((segs) => segs as TranscriptionSegment[])
  })

// ─── Property 7: Minutes Assembly Ordering ────────────────────────────────────

describe('Property 7: sortSegments — Minutes Assembly Ordering', () => {
  it('within the same speakerTurnId, segments are sorted by sequenceNumber ascending', () => {
    fc.assert(
      fc.property(singleTurnSegmentsArb, (segments) => {
        const sorted = sortSegments(segments)

        // All segments belong to the same turn
        const turnId = segments[0]?.speakerTurnId
        if (!turnId) return true

        const turnSegments = sorted.filter((s) => s.speakerTurnId === turnId)
        for (let i = 1; i < turnSegments.length; i++) {
          if (turnSegments[i].sequenceNumber <= turnSegments[i - 1].sequenceNumber) {
            return false
          }
        }
        return true
      }),
      { numRuns: 200 },
    )
  })

  it('output length equals input length (no segments lost or duplicated)', () => {
    fc.assert(
      fc.property(multiTurnSegmentsArb, (segments) => {
        const sorted = sortSegments(segments)
        return sorted.length === segments.length
      }),
      { numRuns: 200 },
    )
  })

  it('output contains exactly the same segments as input (permutation)', () => {
    fc.assert(
      fc.property(multiTurnSegmentsArb, (segments) => {
        const sorted = sortSegments(segments)
        const inputJobIds = new Set(segments.map((s) => s.jobId))
        const outputJobIds = new Set(sorted.map((s) => s.jobId))
        // Every input jobId appears in output and vice versa
        for (const id of inputJobIds) {
          if (!outputJobIds.has(id)) return false
        }
        for (const id of outputJobIds) {
          if (!inputJobIds.has(id)) return false
        }
        return true
      }),
      { numRuns: 200 },
    )
  })

  it('turns are ordered by their earliest segmentStartTime', () => {
    fc.assert(
      fc.property(multiTurnSegmentsArb, (segments) => {
        const sorted = sortSegments(segments)

        // Build earliest start time per turn from the sorted output
        const turnFirstSeen = new Map<string, string>()
        for (const seg of sorted) {
          if (!turnFirstSeen.has(seg.speakerTurnId)) {
            turnFirstSeen.set(seg.speakerTurnId, seg.segmentStartTime)
          }
        }

        // Collect turn order from sorted output
        const turnOrder: string[] = []
        for (const seg of sorted) {
          if (turnOrder[turnOrder.length - 1] !== seg.speakerTurnId) {
            turnOrder.push(seg.speakerTurnId)
          }
        }

        // Verify turns appear in non-decreasing start time order
        for (let i = 1; i < turnOrder.length; i++) {
          const prevStart = turnFirstSeen.get(turnOrder[i - 1])!
          const currStart = turnFirstSeen.get(turnOrder[i])!
          if (currStart < prevStart) return false
        }
        return true
      }),
      { numRuns: 200 },
    )
  })

  it('sortSegments is idempotent (sorting twice gives the same result)', () => {
    fc.assert(
      fc.property(multiTurnSegmentsArb, (segments) => {
        const once = sortSegments(segments)
        const twice = sortSegments(once)
        return JSON.stringify(once) === JSON.stringify(twice)
      }),
      { numRuns: 200 },
    )
  })

  it('does not mutate the input array', () => {
    fc.assert(
      fc.property(multiTurnSegmentsArb, (segments) => {
        const copy = [...segments]
        sortSegments(segments)
        // Input array should be unchanged
        return JSON.stringify(segments) === JSON.stringify(copy)
      }),
      { numRuns: 200 },
    )
  })
})

// ─── Property 5: Adaptive VAD Threshold Function ──────────────────────────────

describe('Property 5: adaptiveVadThreshold — Adaptive VAD Threshold Function', () => {
  it('always returns a value within the valid range for any non-negative duration', () => {
    fc.assert(
      fc.property(
        fc.float({ min: 0, max: 300, noNaN: true }),
        (duration) => {
          const threshold = adaptiveVadThreshold(duration)
          const [lo, hi] = vadThresholdRange(duration)
          return threshold >= lo && threshold <= hi
        },
      ),
      { numRuns: 500 },
    )
  })

  it('threshold for short turns (< 15s) is always >= threshold for long turns (>= 15s)', () => {
    fc.assert(
      fc.property(
        fc.float({ min: 0, max: Math.fround(14.99), noNaN: true }),
        fc.float({ min: 15, max: Math.fround(300), noNaN: true }),
        (shortDuration, longDuration) => {
          const shortThreshold = adaptiveVadThreshold(shortDuration)
          const longThreshold = adaptiveVadThreshold(longDuration)
          // Short turns require longer silence before cutting
          return shortThreshold >= longThreshold
        },
      ),
      { numRuns: 500 },
    )
  })

  it('threshold is deterministic (same input always gives same output)', () => {
    fc.assert(
      fc.property(
        fc.float({ min: 0, max: 300, noNaN: true }),
        (duration) => {
          return adaptiveVadThreshold(duration) === adaptiveVadThreshold(duration)
        },
      ),
      { numRuns: 200 },
    )
  })

  it('threshold is always positive', () => {
    fc.assert(
      fc.property(
        fc.float({ min: 0, max: 300, noNaN: true }),
        (duration) => {
          return adaptiveVadThreshold(duration) > 0
        },
      ),
      { numRuns: 200 },
    )
  })

  it('threshold for short turns is always in [2, 3]', () => {
    fc.assert(
      fc.property(
        fc.float({ min: 0, max: Math.fround(14.99), noNaN: true }),
        (duration) => {
          const t = adaptiveVadThreshold(duration)
          return t >= 2 && t <= 3
        },
      ),
      { numRuns: 300 },
    )
  })

  it('threshold for long turns is always in [0.5, 1]', () => {
    fc.assert(
      fc.property(
        fc.float({ min: 15, max: 300, noNaN: true }),
        (duration) => {
          const t = adaptiveVadThreshold(duration)
          return t >= 0.5 && t <= 1
        },
      ),
      { numRuns: 300 },
    )
  })
})

// ─── Property: float32ToInt16 range invariant ─────────────────────────────────

describe('float32ToInt16 — range invariant', () => {
  it('output values are always in [-32768, 32767]', () => {
    fc.assert(
      fc.property(
        fc.array(fc.float({ noNaN: true }), { minLength: 1, maxLength: 256 }),
        (floats) => {
          const input = new Float32Array(floats)
          const output = float32ToInt16(input)
          for (let i = 0; i < output.length; i++) {
            if (output[i] < -32768 || output[i] > 32767) return false
          }
          return true
        },
      ),
      { numRuns: 300 },
    )
  })

  it('sign is preserved for values in (-1, 0) and (0, 1)', () => {
    fc.assert(
      fc.property(
        fc.float({ min: Math.fround(0.001), max: Math.fround(0.999), noNaN: true }),
        (v) => {
          const pos = float32ToInt16(new Float32Array([v]))[0]
          const neg = float32ToInt16(new Float32Array([-v]))[0]
          return pos > 0 && neg < 0
        },
      ),
      { numRuns: 200 },
    )
  })

  it('output length always equals input length', () => {
    fc.assert(
      fc.property(
        fc.array(fc.float({ noNaN: true }), { minLength: 0, maxLength: 512 }),
        (floats) => {
          const input = new Float32Array(floats)
          const output = float32ToInt16(input)
          return output.length === input.length
        },
      ),
      { numRuns: 200 },
    )
  })
})
