/**
 * Unit tests for audioUtils.ts
 *
 * Covers:
 * - float32ToInt16: conversion correctness, clamping, edge cases
 * - sortSegments: ordering by (speakerTurnId, sequenceNumber), empty input, single segment
 * - adaptiveVadThreshold: threshold values for short/long turns
 * - vadThresholdRange: range bounds
 *
 * Requirements: 20.3
 */

import { describe, it, expect } from 'vitest'
import {
  float32ToInt16,
  sortSegments,
  adaptiveVadThreshold,
  vadThresholdRange,
  type TranscriptionSegment,
} from '../audioUtils'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makeSegment(
  overrides: Partial<TranscriptionSegment> & {
    speakerTurnId: string
    sequenceNumber: number
  },
): TranscriptionSegment {
  return {
    jobId: `job-${overrides.speakerTurnId}-${overrides.sequenceNumber}`,
    speakerId: 1,
    speakerName: 'Speaker',
    text: 'text',
    confidence: null,
    segmentStartTime: '2025-01-01T10:00:00+07:00',
    ...overrides,
  }
}

// ─── float32ToInt16 ───────────────────────────────────────────────────────────

describe('float32ToInt16', () => {
  it('converts 0.0 to 0', () => {
    const input = new Float32Array([0.0])
    const result = float32ToInt16(input)
    expect(result[0]).toBe(0)
  })

  it('converts 1.0 to 32767 (positive max)', () => {
    const input = new Float32Array([1.0])
    const result = float32ToInt16(input)
    expect(result[0]).toBe(32767)
  })

  it('converts -1.0 to -32768 (negative max)', () => {
    const input = new Float32Array([-1.0])
    const result = float32ToInt16(input)
    expect(result[0]).toBe(-32768)
  })

  it('clamps values > 1.0 to 32767', () => {
    const input = new Float32Array([1.5, 2.0, 100.0])
    const result = float32ToInt16(input)
    expect(result[0]).toBe(32767)
    expect(result[1]).toBe(32767)
    expect(result[2]).toBe(32767)
  })

  it('clamps values < -1.0 to -32768', () => {
    const input = new Float32Array([-1.5, -2.0, -100.0])
    const result = float32ToInt16(input)
    expect(result[0]).toBe(-32768)
    expect(result[1]).toBe(-32768)
    expect(result[2]).toBe(-32768)
  })

  it('converts 0.5 to approximately 16384', () => {
    const input = new Float32Array([0.5])
    const result = float32ToInt16(input)
    // 0.5 * 32767 = 16383.5 → rounds to 16384
    expect(result[0]).toBe(16384)
  })

  it('converts -0.5 to approximately -16384', () => {
    const input = new Float32Array([-0.5])
    const result = float32ToInt16(input)
    // -0.5 * 32768 = -16384
    expect(result[0]).toBe(-16384)
  })

  it('returns an Int16Array of the same length as input', () => {
    const input = new Float32Array(4096)
    const result = float32ToInt16(input)
    expect(result).toBeInstanceOf(Int16Array)
    expect(result.length).toBe(4096)
  })

  it('handles empty array', () => {
    const input = new Float32Array(0)
    const result = float32ToInt16(input)
    expect(result).toBeInstanceOf(Int16Array)
    expect(result.length).toBe(0)
  })

  it('preserves sign for small positive values', () => {
    const input = new Float32Array([0.001])
    const result = float32ToInt16(input)
    expect(result[0]).toBeGreaterThan(0)
  })

  it('preserves sign for small negative values', () => {
    const input = new Float32Array([-0.001])
    const result = float32ToInt16(input)
    expect(result[0]).toBeLessThan(0)
  })
})

// ─── sortSegments ─────────────────────────────────────────────────────────────

describe('sortSegments', () => {
  it('returns empty array for empty input', () => {
    expect(sortSegments([])).toEqual([])
  })

  it('returns single segment unchanged', () => {
    const seg = makeSegment({ speakerTurnId: 'turn-1', sequenceNumber: 1 })
    expect(sortSegments([seg])).toEqual([seg])
  })

  it('sorts segments within the same turn by sequenceNumber ascending', () => {
    const seg3 = makeSegment({ speakerTurnId: 'turn-1', sequenceNumber: 3 })
    const seg1 = makeSegment({ speakerTurnId: 'turn-1', sequenceNumber: 1 })
    const seg2 = makeSegment({ speakerTurnId: 'turn-1', sequenceNumber: 2 })

    const result = sortSegments([seg3, seg1, seg2])
    expect(result.map((s) => s.sequenceNumber)).toEqual([1, 2, 3])
  })

  it('orders different turns by their earliest segmentStartTime', () => {
    const turnA_seg1 = makeSegment({
      speakerTurnId: 'turn-A',
      sequenceNumber: 1,
      segmentStartTime: '2025-01-01T10:00:00+07:00',
    })
    const turnA_seg2 = makeSegment({
      speakerTurnId: 'turn-A',
      sequenceNumber: 2,
      segmentStartTime: '2025-01-01T10:00:05+07:00',
    })
    const turnB_seg1 = makeSegment({
      speakerTurnId: 'turn-B',
      sequenceNumber: 1,
      segmentStartTime: '2025-01-01T10:01:00+07:00',
    })

    // Input in reverse order
    const result = sortSegments([turnB_seg1, turnA_seg2, turnA_seg1])
    expect(result[0].speakerTurnId).toBe('turn-A')
    expect(result[1].speakerTurnId).toBe('turn-A')
    expect(result[2].speakerTurnId).toBe('turn-B')
    // Within turn-A, sequenceNumber order preserved
    expect(result[0].sequenceNumber).toBe(1)
    expect(result[1].sequenceNumber).toBe(2)
  })

  it('does not mutate the original array', () => {
    const seg2 = makeSegment({ speakerTurnId: 'turn-1', sequenceNumber: 2 })
    const seg1 = makeSegment({ speakerTurnId: 'turn-1', sequenceNumber: 1 })
    const original = [seg2, seg1]
    sortSegments(original)
    // Original should be unchanged
    expect(original[0].sequenceNumber).toBe(2)
    expect(original[1].sequenceNumber).toBe(1)
  })

  it('handles multiple turns with interleaved arrival order', () => {
    // Simulate segments arriving out of order from the network
    const segments = [
      makeSegment({ speakerTurnId: 'turn-B', sequenceNumber: 2, segmentStartTime: '2025-01-01T10:02:00+07:00' }),
      makeSegment({ speakerTurnId: 'turn-A', sequenceNumber: 3, segmentStartTime: '2025-01-01T10:00:10+07:00' }),
      makeSegment({ speakerTurnId: 'turn-B', sequenceNumber: 1, segmentStartTime: '2025-01-01T10:01:00+07:00' }),
      makeSegment({ speakerTurnId: 'turn-A', sequenceNumber: 1, segmentStartTime: '2025-01-01T10:00:00+07:00' }),
      makeSegment({ speakerTurnId: 'turn-A', sequenceNumber: 2, segmentStartTime: '2025-01-01T10:00:05+07:00' }),
    ]

    const result = sortSegments(segments)

    // All turn-A segments come first (earlier start time)
    expect(result[0].speakerTurnId).toBe('turn-A')
    expect(result[1].speakerTurnId).toBe('turn-A')
    expect(result[2].speakerTurnId).toBe('turn-A')
    // Within turn-A: ascending sequence
    expect(result[0].sequenceNumber).toBe(1)
    expect(result[1].sequenceNumber).toBe(2)
    expect(result[2].sequenceNumber).toBe(3)
    // turn-B comes after
    expect(result[3].speakerTurnId).toBe('turn-B')
    expect(result[4].speakerTurnId).toBe('turn-B')
    expect(result[3].sequenceNumber).toBe(1)
    expect(result[4].sequenceNumber).toBe(2)
  })
})

// ─── adaptiveVadThreshold ─────────────────────────────────────────────────────

describe('adaptiveVadThreshold', () => {
  it('returns a value in [2, 3] for duration < 15s', () => {
    const threshold = adaptiveVadThreshold(0)
    expect(threshold).toBeGreaterThanOrEqual(2)
    expect(threshold).toBeLessThanOrEqual(3)
  })

  it('returns a value in [2, 3] for duration = 14.9s', () => {
    const threshold = adaptiveVadThreshold(14.9)
    expect(threshold).toBeGreaterThanOrEqual(2)
    expect(threshold).toBeLessThanOrEqual(3)
  })

  it('returns a value in [0.5, 1] for duration = 15s', () => {
    const threshold = adaptiveVadThreshold(15)
    expect(threshold).toBeGreaterThanOrEqual(0.5)
    expect(threshold).toBeLessThanOrEqual(1)
  })

  it('returns a value in [0.5, 1] for duration > 15s', () => {
    const threshold = adaptiveVadThreshold(30)
    expect(threshold).toBeGreaterThanOrEqual(0.5)
    expect(threshold).toBeLessThanOrEqual(1)
  })

  it('throws for negative duration', () => {
    expect(() => adaptiveVadThreshold(-1)).toThrow(RangeError)
  })
})

// ─── vadThresholdRange ────────────────────────────────────────────────────────

describe('vadThresholdRange', () => {
  it('returns [2, 3] for duration < 15s', () => {
    expect(vadThresholdRange(0)).toEqual([2, 3])
    expect(vadThresholdRange(10)).toEqual([2, 3])
    expect(vadThresholdRange(14.99)).toEqual([2, 3])
  })

  it('returns [0.5, 1] for duration >= 15s', () => {
    expect(vadThresholdRange(15)).toEqual([0.5, 1])
    expect(vadThresholdRange(20)).toEqual([0.5, 1])
    expect(vadThresholdRange(30)).toEqual([0.5, 1])
  })
})
