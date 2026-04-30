/**
 * audioUtils.ts — shared audio processing utilities.
 *
 * Used by useAudioCapture hook and unit/property tests.
 * Requirements: 8.14
 */

// ─── PCM conversion ───────────────────────────────────────────────────────────

/**
 * Convert a Float32Array of PCM samples (range [-1.0, 1.0]) to a Int16Array
 * (range [-32768, 32767]).
 *
 * This is the format expected by the backend WebSocket audio stream handler
 * and by Gipformer for WAV 16kHz mono conversion.
 *
 * Clamps values outside [-1.0, 1.0] to prevent overflow.
 *
 * Requirements: 8.14
 */
export function float32ToInt16(float32: Float32Array): Int16Array {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    // Clamp to [-1, 1] then scale to Int16 range
    const clamped = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = clamped < 0
      ? Math.round(clamped * 32768)
      : Math.round(clamped * 32767)
  }
  return int16
}

// ─── Transcription segment sorting ───────────────────────────────────────────

export interface TranscriptionSegment {
  jobId: string
  speakerId: number
  speakerName: string
  speakerTurnId: string
  sequenceNumber: number
  text: string
  confidence: number | null
  segmentStartTime: string
}

/**
 * Sort transcription segments by (speakerTurnId, sequenceNumber).
 *
 * The sort is stable: segments with the same speakerTurnId are ordered by
 * ascending sequenceNumber. Different speaker turns are ordered by their
 * first segment's segmentStartTime (chronological meeting order).
 *
 * This matches the backend minutes assembly ordering described in Req 25.1
 * and the frontend display ordering in Req 8.12.
 *
 * Requirements: 8.12, 25.1
 */
export function sortSegments(segments: TranscriptionSegment[]): TranscriptionSegment[] {
  if (segments.length === 0) return []

  // Build a map of speakerTurnId → earliest segmentStartTime for inter-turn ordering
  const turnStartTime = new Map<string, string>()
  for (const seg of segments) {
    const existing = turnStartTime.get(seg.speakerTurnId)
    if (!existing || seg.segmentStartTime < existing) {
      turnStartTime.set(seg.speakerTurnId, seg.segmentStartTime)
    }
  }

  return [...segments].sort((a, b) => {
    // Primary: order turns by their earliest segment start time
    const aStart = turnStartTime.get(a.speakerTurnId) ?? a.segmentStartTime
    const bStart = turnStartTime.get(b.speakerTurnId) ?? b.segmentStartTime

    if (a.speakerTurnId !== b.speakerTurnId) {
      if (aStart < bStart) return -1
      if (aStart > bStart) return 1
      // Tie-break by speakerTurnId string for determinism
      return a.speakerTurnId.localeCompare(b.speakerTurnId)
    }

    // Secondary: within the same turn, order by sequenceNumber ascending
    return a.sequenceNumber - b.sequenceNumber
  })
}

// ─── Adaptive VAD threshold ───────────────────────────────────────────────────

/**
 * Compute the adaptive VAD silence threshold (in seconds) based on the
 * accumulated audio duration of the current speaker turn.
 *
 * Rules (mirroring Gipformer backend logic, Req 8.9):
 * - If accumulated duration < 15s → silence threshold is in [2, 3] seconds
 * - If accumulated duration >= 15s → silence threshold is in [0.5, 1] second
 *
 * Returns the threshold value to use for VAD silence detection.
 * The frontend uses this to decide when to finalize an audio chunk.
 *
 * Requirements: 8.9
 */
export function adaptiveVadThreshold(accumulatedDurationSeconds: number): number {
  if (accumulatedDurationSeconds < 0) {
    throw new RangeError('accumulatedDurationSeconds must be >= 0')
  }
  // Short turn: require longer silence before cutting (avoid premature cuts)
  if (accumulatedDurationSeconds < 15) {
    return 2.5 // midpoint of [2, 3]
  }
  // Long turn: cut sooner to keep chunks manageable
  return 0.75 // midpoint of [0.5, 1]
}

/**
 * Returns the valid silence threshold range for a given accumulated duration.
 * Used in property tests to validate that adaptiveVadThreshold stays in bounds.
 *
 * Requirements: 8.9
 */
export function vadThresholdRange(accumulatedDurationSeconds: number): [number, number] {
  if (accumulatedDurationSeconds < 15) {
    return [2, 3]
  }
  return [0.5, 1]
}
