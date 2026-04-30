import { create } from 'zustand'
import type {
  Meeting,
  MeetingMode,
  Participant,
  SpeakingPermission,
  RaiseHandRequest,
  MeetingEvent,
  ModeChangedPayload,
  RaiseHandPayload,
  SpeakingPermissionPayload,
  ParticipantPayload,
} from '../types/meeting'

// ─── State & actions interface ────────────────────────────────────────────────

interface MeetingState {
  /** The currently active meeting the local user has joined, or null */
  activeMeeting: Meeting | null
  /** Current meeting mode */
  mode: MeetingMode
  /** All participants currently connected in the meeting */
  participants: Participant[]
  /** The participant who currently holds speaking permission, or null */
  speakingPermission: SpeakingPermission | null
  /** Pending raise-hand requests (chronological, oldest first) */
  raiseHandRequests: RaiseHandRequest[]
  /** Whether Gipformer transcription service is available */
  isTranscriptionAvailable: boolean

  // ── Lifecycle ────────────────────────────────────────────────────────────
  /** Set the active meeting when the user joins */
  setActiveMeeting: (meeting: Meeting) => void
  /** Clear active meeting state when the user leaves or meeting ends */
  clearActiveMeeting: () => void

  // ── Mode ─────────────────────────────────────────────────────────────────
  /** Update the meeting mode (called on MODE_CHANGED WebSocket event) */
  setMode: (mode: MeetingMode) => void

  // ── Participants ─────────────────────────────────────────────────────────
  /** Replace the full participant list (e.g. on initial join) */
  setParticipants: (participants: Participant[]) => void
  /** Add or update a participant (called on PARTICIPANT_JOINED event) */
  addParticipant: (participant: Participant) => void
  /** Mark a participant as disconnected (called on PARTICIPANT_LEFT event) */
  removeParticipant: (userId: number) => void

  // ── Speaking permission ───────────────────────────────────────────────────
  /** Set the current speaker (called on SPEAKING_PERMISSION_GRANTED event) */
  setSpeakingPermission: (permission: SpeakingPermission) => void
  /** Clear the current speaker (called on SPEAKING_PERMISSION_REVOKED event) */
  clearSpeakingPermission: () => void

  // ── Raise hand ────────────────────────────────────────────────────────────
  /** Add a raise-hand request (called on RAISE_HAND event) */
  addRaiseHandRequest: (request: RaiseHandRequest) => void
  /** Remove a raise-hand request by userId (called on RAISE_HAND_CANCELLED or SPEAKING_PERMISSION_GRANTED) */
  removeRaiseHandRequest: (userId: number) => void
  /** Clear all pending raise-hand requests */
  clearRaiseHandRequests: () => void

  // ── Transcription availability ────────────────────────────────────────────
  setTranscriptionAvailable: (available: boolean) => void

  // ── WebSocket event dispatcher ────────────────────────────────────────────
  /**
   * Central handler — dispatch an incoming MeetingEvent to the appropriate
   * state mutation. Call this from the useWebSocket onMeetingEvent callback.
   */
  handleMeetingEvent: (event: MeetingEvent) => void
}

// ─── Store ────────────────────────────────────────────────────────────────────

/**
 * Meeting store — holds all real-time state for the active meeting session.
 *
 * Designed to be driven by WebSocket events via `handleMeetingEvent`.
 * Components read from this store and react to state changes.
 *
 * Requirements: 1.7
 */
const useMeetingStore = create<MeetingState>((set, get) => ({
  activeMeeting: null,
  mode: 'FREE_MODE',
  participants: [],
  speakingPermission: null,
  raiseHandRequests: [],
  isTranscriptionAvailable: true,

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  setActiveMeeting: (meeting) => {
    set({
      activeMeeting: meeting,
      mode: meeting.mode,
      participants: [],
      speakingPermission: null,
      raiseHandRequests: [],
      isTranscriptionAvailable: true,
    })
  },

  clearActiveMeeting: () => {
    set({
      activeMeeting: null,
      mode: 'FREE_MODE',
      participants: [],
      speakingPermission: null,
      raiseHandRequests: [],
      isTranscriptionAvailable: true,
    })
  },

  // ── Mode ───────────────────────────────────────────────────────────────────

  setMode: (mode) => {
    set({ mode })
    // When switching to FREE_MODE, clear all raise-hand requests and speaking permission
    if (mode === 'FREE_MODE') {
      set({ speakingPermission: null, raiseHandRequests: [] })
    }
  },

  // ── Participants ───────────────────────────────────────────────────────────

  setParticipants: (participants) => {
    set({ participants })
  },

  addParticipant: (participant) => {
    const existing = get().participants
    const idx = existing.findIndex((p) => p.userId === participant.userId)
    if (idx >= 0) {
      // Update existing entry (e.g. reconnect)
      const updated = [...existing]
      updated[idx] = { ...existing[idx], ...participant, isConnected: true }
      set({ participants: updated })
    } else {
      set({ participants: [...existing, { ...participant, isConnected: true }] })
    }
  },

  removeParticipant: (userId) => {
    const participants = get().participants.map((p) =>
      p.userId === userId ? { ...p, isConnected: false } : p,
    )
    set({ participants })
  },

  // ── Speaking permission ────────────────────────────────────────────────────

  setSpeakingPermission: (permission) => {
    set({ speakingPermission: permission })
    // Remove the granted user from the raise-hand queue
    get().removeRaiseHandRequest(permission.userId)
  },

  clearSpeakingPermission: () => {
    set({ speakingPermission: null })
  },

  // ── Raise hand ─────────────────────────────────────────────────────────────

  addRaiseHandRequest: (request) => {
    const existing = get().raiseHandRequests
    // Deduplicate — a user can only have one pending request
    if (existing.some((r) => r.userId === request.userId)) return
    // Maintain chronological order (oldest first)
    set({ raiseHandRequests: [...existing, request] })
  },

  removeRaiseHandRequest: (userId) => {
    set({
      raiseHandRequests: get().raiseHandRequests.filter((r) => r.userId !== userId),
    })
  },

  clearRaiseHandRequests: () => {
    set({ raiseHandRequests: [] })
  },

  // ── Transcription availability ─────────────────────────────────────────────

  setTranscriptionAvailable: (available) => {
    set({ isTranscriptionAvailable: available })
  },

  // ── WebSocket event dispatcher ─────────────────────────────────────────────

  handleMeetingEvent: (event) => {
    const { type, payload } = event

    switch (type) {
      case 'MODE_CHANGED': {
        const { mode } = payload as ModeChangedPayload
        get().setMode(mode)
        break
      }

      case 'RAISE_HAND': {
        const { userId, userName, requestedAt } = payload as RaiseHandPayload
        get().addRaiseHandRequest({ userId, userName, requestedAt })
        break
      }

      case 'RAISE_HAND_CANCELLED': {
        const { userId } = payload as RaiseHandPayload
        get().removeRaiseHandRequest(userId)
        break
      }

      case 'SPEAKING_PERMISSION_GRANTED': {
        const { userId, userName, speakerTurnId } = payload as SpeakingPermissionPayload
        get().setSpeakingPermission({
          userId,
          userName,
          speakerTurnId,
          grantedAt: event.timestamp,
        })
        break
      }

      case 'SPEAKING_PERMISSION_REVOKED': {
        get().clearSpeakingPermission()
        break
      }

      case 'PARTICIPANT_JOINED': {
        const { userId, userName } = payload as ParticipantPayload
        get().addParticipant({ userId, userName, isConnected: true })
        break
      }

      case 'PARTICIPANT_LEFT': {
        const { userId } = payload as ParticipantPayload
        get().removeParticipant(userId)
        // Auto-revoke speaking permission if the speaker left
        if (get().speakingPermission?.userId === userId) {
          get().clearSpeakingPermission()
        }
        break
      }

      case 'MEETING_ENDED': {
        get().clearActiveMeeting()
        break
      }

      case 'TRANSCRIPTION_UNAVAILABLE': {
        get().setTranscriptionAvailable(false)
        break
      }

      // MEETING_STARTED, TRANSCRIPTION_SEGMENT, MINUTES_PUBLISHED
      // are handled by other hooks/components that subscribe to the store
      default:
        break
    }
  },
}))

export default useMeetingStore
