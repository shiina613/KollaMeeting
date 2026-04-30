/**
 * useAudioCapture — captures microphone audio and streams raw PCM Int16 frames
 * to the backend via a binary WebSocket connection.
 *
 * Pipeline:
 *   getUserMedia() → AudioContext (16kHz) → ScriptProcessorNode (4096 samples)
 *   → Float32 → Int16 conversion → binary WebSocket send
 *
 * Features:
 * - Requests microphone permission on `startCapture()`
 * - Shows a permission-denied state when the user blocks the mic
 * - Resamples to 16kHz mono (required by Gipformer WAV input)
 * - Sends Int16 PCM frames as ArrayBuffer over a dedicated binary WebSocket
 * - Cleans up AudioContext, MediaStream, and WebSocket on `stopCapture()` / unmount
 *
 * Requirements: 8.14
 */

import { useCallback, useEffect, useRef, useState } from 'react'

// ─── Constants ────────────────────────────────────────────────────────────────

/** Target sample rate for Gipformer (WAV 16kHz mono). */
const TARGET_SAMPLE_RATE = 16_000

/** ScriptProcessorNode buffer size (samples per channel per callback). */
const BUFFER_SIZE = 4_096

/** WebSocket URL for the audio stream endpoint. */
const AUDIO_WS_URL =
  import.meta.env.VITE_AUDIO_WS_URL ?? 'ws://localhost:8080/ws/audio'

// ─── Types ────────────────────────────────────────────────────────────────────

export type AudioCaptureStatus =
  | 'idle'          // Not capturing
  | 'requesting'    // Waiting for getUserMedia permission
  | 'capturing'     // Actively capturing and streaming
  | 'denied'        // Microphone permission denied
  | 'error'         // Unexpected error

export interface UseAudioCaptureOptions {
  /** Meeting ID — sent as a query param on the WebSocket URL so the backend
   *  can route the audio stream to the correct meeting. */
  meetingId: number
  /** Speaker turn ID — identifies the current speaking turn. Must be updated
   *  each time the Host grants speaking permission. */
  speakerTurnId: string | null
  /** Called when the WebSocket connection is established. */
  onConnected?: () => void
  /** Called when the WebSocket connection is closed. */
  onDisconnected?: () => void
  /** Called when an error occurs (permission denied, WebSocket error, etc.). */
  onError?: (error: Error) => void
}

export interface UseAudioCaptureReturn {
  /** Current capture status. */
  status: AudioCaptureStatus
  /** Start capturing audio and streaming to the backend. */
  startCapture: () => Promise<void>
  /** Stop capturing audio and close the WebSocket. */
  stopCapture: () => void
  /** Whether the hook is actively streaming audio. */
  isCapturing: boolean
  /** Whether the microphone permission was denied. */
  isPermissionDenied: boolean
}

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useAudioCapture
 *
 * Requirements: 8.14
 */
export function useAudioCapture({
  meetingId,
  speakerTurnId,
  onConnected,
  onDisconnected,
  onError,
}: UseAudioCaptureOptions): UseAudioCaptureReturn {
  const [status, setStatus] = useState<AudioCaptureStatus>('idle')

  // Refs — avoid stale closures in audio callbacks
  const audioContextRef = useRef<AudioContext | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const processorNodeRef = useRef<ScriptProcessorNode | null>(null)
  const sourceNodeRef = useRef<MediaStreamAudioSourceNode | null>(null)
  const wsRef = useRef<WebSocket | null>(null)
  const isMountedRef = useRef(true)
  const speakerTurnIdRef = useRef(speakerTurnId)
  const onConnectedRef = useRef(onConnected)
  const onDisconnectedRef = useRef(onDisconnected)
  const onErrorRef = useRef(onError)

  // Keep callback refs fresh
  useEffect(() => { speakerTurnIdRef.current = speakerTurnId }, [speakerTurnId])
  useEffect(() => { onConnectedRef.current = onConnected }, [onConnected])
  useEffect(() => { onDisconnectedRef.current = onDisconnected }, [onDisconnected])
  useEffect(() => { onErrorRef.current = onError }, [onError])

  // ── Cleanup helpers ────────────────────────────────────────────────────────

  const closeWebSocket = useCallback(() => {
    if (wsRef.current) {
      try { wsRef.current.close() } catch { /* ignore */ }
      wsRef.current = null
    }
  }, [])

  const stopAudioPipeline = useCallback(() => {
    // Disconnect ScriptProcessorNode
    if (processorNodeRef.current) {
      try { processorNodeRef.current.disconnect() } catch { /* ignore */ }
      processorNodeRef.current = null
    }
    // Disconnect source node
    if (sourceNodeRef.current) {
      try { sourceNodeRef.current.disconnect() } catch { /* ignore */ }
      sourceNodeRef.current = null
    }
    // Stop all media tracks
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((t) => t.stop())
      mediaStreamRef.current = null
    }
    // Close AudioContext
    if (audioContextRef.current) {
      audioContextRef.current.close().catch(() => { /* ignore */ })
      audioContextRef.current = null
    }
  }, [])

  // ── stopCapture ────────────────────────────────────────────────────────────

  const stopCapture = useCallback(() => {
    stopAudioPipeline()
    closeWebSocket()
    if (isMountedRef.current) {
      setStatus('idle')
    }
  }, [stopAudioPipeline, closeWebSocket])

  // ── startCapture ───────────────────────────────────────────────────────────

  const startCapture = useCallback(async () => {
    if (!isMountedRef.current) return

    // Stop any existing capture first
    stopAudioPipeline()
    closeWebSocket()

    setStatus('requesting')

    // ── 1. Request microphone permission ──────────────────────────────────

    let stream: MediaStream
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          channelCount: 1,
          sampleRate: TARGET_SAMPLE_RATE,
          echoCancellation: true,
          noiseSuppression: true,
        },
        video: false,
      })
    } catch (err) {
      if (!isMountedRef.current) return

      const isDenied =
        err instanceof DOMException &&
        (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError')

      setStatus(isDenied ? 'denied' : 'error')
      const error = err instanceof Error ? err : new Error(String(err))
      onErrorRef.current?.(error)
      return
    }

    if (!isMountedRef.current) {
      stream.getTracks().forEach((t) => t.stop())
      return
    }

    mediaStreamRef.current = stream

    // ── 2. Build AudioContext at 16kHz ────────────────────────────────────

    const audioContext = new AudioContext({ sampleRate: TARGET_SAMPLE_RATE })
    audioContextRef.current = audioContext

    // ── 3. Connect pipeline: source → ScriptProcessorNode ─────────────────

    const sourceNode = audioContext.createMediaStreamSource(stream)
    sourceNodeRef.current = sourceNode

    // ScriptProcessorNode is deprecated but remains the most compatible way
    // to get raw PCM frames in a browser without AudioWorklet complexity.
    // Buffer size 4096 gives ~256ms at 16kHz — acceptable latency.
    const processorNode = audioContext.createScriptProcessor(BUFFER_SIZE, 1, 1)
    processorNodeRef.current = processorNode

    // ── 4. Open binary WebSocket ──────────────────────────────────────────

    const wsUrl = `${AUDIO_WS_URL}?meetingId=${meetingId}&speakerTurnId=${speakerTurnIdRef.current ?? ''}`
    const ws = new WebSocket(wsUrl)
    ws.binaryType = 'arraybuffer'
    wsRef.current = ws

    ws.onopen = () => {
      if (!isMountedRef.current) {
        ws.close()
        return
      }
      // Connect audio pipeline only after WebSocket is open
      sourceNode.connect(processorNode)
      processorNode.connect(audioContext.destination)
      setStatus('capturing')
      onConnectedRef.current?.()
    }

    ws.onclose = () => {
      if (!isMountedRef.current) return
      stopAudioPipeline()
      setStatus('idle')
      onDisconnectedRef.current?.()
    }

    ws.onerror = () => {
      if (!isMountedRef.current) return
      stopAudioPipeline()
      setStatus('error')
      onErrorRef.current?.(new Error('Audio WebSocket connection error'))
    }

    // ── 5. PCM callback: Float32 → Int16 → send binary ────────────────────

    processorNode.onaudioprocess = (event: AudioProcessingEvent) => {
      if (ws.readyState !== WebSocket.OPEN) return

      const float32 = event.inputBuffer.getChannelData(0)
      const int16 = float32ToInt16Internal(float32)
      ws.send(int16.buffer)
    }
  }, [meetingId, stopAudioPipeline, closeWebSocket])

  // ── Cleanup on unmount ─────────────────────────────────────────────────────

  useEffect(() => {
    isMountedRef.current = true
    return () => {
      isMountedRef.current = false
      stopAudioPipeline()
      closeWebSocket()
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  // ── Derived state ──────────────────────────────────────────────────────────

  return {
    status,
    startCapture,
    stopCapture,
    isCapturing: status === 'capturing',
    isPermissionDenied: status === 'denied',
  }
}

// ─── Internal helpers ─────────────────────────────────────────────────────────

/**
 * Convert Float32Array PCM samples to Int16Array.
 * Kept internal to the hook module; the exported version lives in audioUtils.ts.
 * This avoids a circular import between the hook and the utility module.
 */
function float32ToInt16Internal(float32: Float32Array): Int16Array {
  const int16 = new Int16Array(float32.length)
  for (let i = 0; i < float32.length; i++) {
    const clamped = Math.max(-1, Math.min(1, float32[i]))
    int16[i] = clamped < 0
      ? Math.round(clamped * 32768)
      : Math.round(clamped * 32767)
  }
  return int16
}

export default useAudioCapture
