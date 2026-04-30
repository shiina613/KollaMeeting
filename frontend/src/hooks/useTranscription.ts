/**
 * useTranscription — buffers incoming TRANSCRIPTION_SEGMENT WebSocket events
 * and maintains a sorted list of segments ordered by (speakerTurnId, sequenceNumber).
 *
 * Features:
 * - Accepts new segments via `addSegment()` (called from the WebSocket event handler)
 * - Deduplicates by jobId (idempotency — same job may arrive twice)
 * - Sorts segments using the shared `sortSegments` utility (Req 8.12, 25.1)
 * - Exposes `clearSegments()` to reset state when a meeting ends
 * - Tracks `isTranscriptionAvailable` based on TRANSCRIPTION_UNAVAILABLE events
 *
 * Requirements: 8.12
 */

import { useCallback, useRef, useState } from 'react'
import { sortSegments, type TranscriptionSegment } from '../utils/audioUtils'
import type { MeetingEvent, TranscriptionSegmentPayload } from '../types/meeting'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface UseTranscriptionReturn {
  /** Sorted transcription segments (by speakerTurnId then sequenceNumber). */
  segments: TranscriptionSegment[]
  /** Whether the Gipformer transcription service is currently available. */
  isTranscriptionAvailable: boolean
  /** Add a new segment (or ignore if duplicate jobId). */
  addSegment: (segment: TranscriptionSegment) => void
  /** Handle a raw MeetingEvent — dispatches TRANSCRIPTION_SEGMENT and
   *  TRANSCRIPTION_UNAVAILABLE events automatically. */
  handleTranscriptionEvent: (event: MeetingEvent) => void
  /** Clear all buffered segments (e.g. when meeting ends). */
  clearSegments: () => void
  /** Mark transcription service as available again (e.g. on reconnect). */
  setTranscriptionAvailable: (available: boolean) => void
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useTranscription
 *
 * Requirements: 8.12
 */
export function useTranscription(): UseTranscriptionReturn {
  const [segments, setSegments] = useState<TranscriptionSegment[]>([])
  const [isTranscriptionAvailable, setIsTranscriptionAvailable] = useState(true)

  // Use a ref to track seen jobIds for O(1) deduplication without re-renders
  const seenJobIdsRef = useRef<Set<string>>(new Set())

  // ── addSegment ─────────────────────────────────────────────────────────────

  const addSegment = useCallback((segment: TranscriptionSegment) => {
    // Idempotency: ignore duplicate jobIds (Req 8.12 — callback idempotency)
    if (seenJobIdsRef.current.has(segment.jobId)) return
    seenJobIdsRef.current.add(segment.jobId)

    setSegments((prev) => sortSegments([...prev, segment]))
  }, [])

  // ── handleTranscriptionEvent ───────────────────────────────────────────────

  const handleTranscriptionEvent = useCallback(
    (event: MeetingEvent) => {
      if (event.type === 'TRANSCRIPTION_SEGMENT') {
        const payload = event.payload as TranscriptionSegmentPayload
        addSegment({
          jobId: payload.jobId,
          speakerId: payload.speakerId,
          speakerName: payload.speakerName,
          speakerTurnId: payload.speakerTurnId,
          sequenceNumber: payload.sequenceNumber,
          text: payload.text,
          confidence: payload.confidence,
          segmentStartTime: payload.segmentStartTime,
        })
      } else if (event.type === 'TRANSCRIPTION_UNAVAILABLE') {
        setIsTranscriptionAvailable(false)
      }
    },
    [addSegment],
  )

  // ── clearSegments ──────────────────────────────────────────────────────────

  const clearSegments = useCallback(() => {
    seenJobIdsRef.current.clear()
    setSegments([])
  }, [])

  // ── setTranscriptionAvailable ──────────────────────────────────────────────

  const setTranscriptionAvailable = useCallback((available: boolean) => {
    setIsTranscriptionAvailable(available)
  }, [])

  return {
    segments,
    isTranscriptionAvailable,
    addSegment,
    handleTranscriptionEvent,
    clearSegments,
    setTranscriptionAvailable,
  }
}

export default useTranscription
