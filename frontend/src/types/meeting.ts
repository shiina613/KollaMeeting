// ─── Meeting enums ────────────────────────────────────────────────────────────

export type MeetingStatus = 'SCHEDULED' | 'ACTIVE' | 'ENDED'
export type MeetingMode = 'FREE_MODE' | 'MEETING_MODE'
export type TranscriptionPriority = 'HIGH_PRIORITY' | 'NORMAL_PRIORITY'

// ─── Meeting event types (WebSocket STOMP) ────────────────────────────────────

export type MeetingEventType =
  | 'MEETING_STARTED'
  | 'MEETING_ENDED'
  | 'MODE_CHANGED'
  | 'RAISE_HAND'
  | 'RAISE_HAND_CANCELLED'
  | 'SPEAKING_PERMISSION_GRANTED'
  | 'SPEAKING_PERMISSION_REVOKED'
  | 'TRANSCRIPTION_SEGMENT'
  | 'TRANSCRIPTION_UNAVAILABLE'
  | 'MINUTES_PUBLISHED'
  | 'PARTICIPANT_JOINED'
  | 'PARTICIPANT_LEFT'

export interface MeetingEvent<T = unknown> {
  type: MeetingEventType
  meetingId: number
  timestamp: string // ISO 8601 UTC+7
  payload: T
}

// ─── Specific event payloads ──────────────────────────────────────────────────

export interface MeetingStartedPayload {
  meetingId: number
  hostName: string
}

export interface ModeChangedPayload {
  mode: MeetingMode
}

export interface RaiseHandPayload {
  userId: number
  userName: string
  requestedAt: string
}

export interface SpeakingPermissionPayload {
  userId: number
  userName: string
  speakerTurnId: string
}

export interface TranscriptionSegmentPayload {
  jobId: string
  speakerId: number
  speakerName: string
  speakerTurnId: string
  sequenceNumber: number
  text: string
  confidence: number | null
  segmentStartTime: string
}

export interface ParticipantPayload {
  userId: number
  userName: string
}

// ─── Participant in meeting ───────────────────────────────────────────────────

export interface Participant {
  userId: number
  userName: string
  isConnected: boolean
}

// ─── Speaking permission ──────────────────────────────────────────────────────

export interface SpeakingPermission {
  userId: number
  userName: string
  speakerTurnId: string
  grantedAt: string
}

// ─── Raise hand request ───────────────────────────────────────────────────────

export interface RaiseHandRequest {
  userId: number
  userName: string
  requestedAt: string
}

// ─── Meeting detail ───────────────────────────────────────────────────────────

export interface Meeting {
  id: number
  title: string
  description?: string
  meetingCode: string
  status: MeetingStatus
  mode: MeetingMode
  transcriptionPriority: TranscriptionPriority
  startTime: string
  endTime: string
  roomId: number
  roomName?: string
  departmentId?: number
  departmentName?: string
  hostUserId: number
  hostUserName?: string
  secretaryUserId: number
  secretaryUserName?: string
  createdAt: string
}
