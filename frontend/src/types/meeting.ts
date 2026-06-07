// ─── Meeting enums ────────────────────────────────────────────────────────────

export type MeetingStatus = 'SCHEDULED' | 'ACTIVE' | 'ENDED'
export type MeetingMode = 'FREE_MODE' | 'MEETING_MODE'
export type TranscriptionPriority = 'HIGH_PRIORITY' | 'NORMAL_PRIORITY'
export type MeetingRole =
  | 'HOST'
  | 'SECRETARY'
  | 'REVIEWER'
  | 'COMMITTEE_MEMBER'
  | 'GUEST'
  | 'MEMBER'

// ─── Meeting event types (WebSocket STOMP) ────────────────────────────────────

export type MeetingEventType =
  | 'MEETING_STARTED'
  | 'MEETING_ENDED'
  | 'MODE_CHANGED'
  | 'RAISE_HAND'
  | 'HAND_LOWERED'
  | 'SPEAKING_PERMISSION_GRANTED'
  | 'SPEAKING_PERMISSION_REVOKED'
  | 'HOST_TRANSFERRED'
  | 'HOST_RESTORED'
  | 'WAITING_TIMEOUT_STARTED'
  | 'WAITING_TIMEOUT_CANCELLED'
  | 'TRANSCRIPTION_SEGMENT'
  | 'PRIORITY_CHANGED'
  | 'TRANSCRIPTION_UNAVAILABLE'
  | 'TRANSCRIPTION_RECOVERED'
  | 'DOCUMENT_UPLOADED'
  | 'MEETING_MESSAGE_CREATED'
  | 'MINUTES_READY'
  | 'MINUTES_CONFIRMED'
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
  speakerDept?: string
  speakerRole?: MeetingRole
  meetingRole?: MeetingRole
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

// ─── Domain models ────────────────────────────────────────────────────────────

export interface Department {
  id: number
  name: string
  departmentCode?: string
  description?: string
}

export interface Room {
  id: number
  name: string
  roomName?: string
  roomCode?: string
  capacity?: number
  departmentId?: number
  departmentName?: string
  department: Department
}

export interface MeetingUser {
  id: number
  username: string
  employeeCode?: string
  fullName: string
  email: string
  role: 'ADMIN' | 'SECRETARY' | 'USER'
  department?: Department
  departmentName?: string
  isActive: boolean
  dob?: string
  phoneNumber?: string
  degree?: string
  identification?: string
  address?: string
  bankName?: string
  bankNumber?: string
  img?: string
}

export interface Meeting {
  id: number
  title: string
  name?: string
  description?: string
  meetingCode: string
  status: MeetingStatus
  mode: MeetingMode
  transcriptionPriority: TranscriptionPriority
  startTime: string  // ISO8601 UTC+7
  endTime: string
  room: Room
  hostUser: MeetingUser
  secretaryUser: MeetingUser
  createdBy: MeetingUser
  memberCount?: number
  // Legacy flat fields (kept for backward compat)
  roomId?: number
  roomName?: string
  departmentId?: number
  departmentName?: string
  hostUserId?: number
  hostUserName?: string
  hostDepartmentName?: string
  // Flat host/secretary fields from backend MeetingResponse
  hostId?: number
  hostName?: string
  secretaryId?: number
  secretaryName?: string
  secretaryUserId?: number
  secretaryUserName?: string
  secretaryDepartmentName?: string
  createdAt?: string
}

export interface MeetingMember {
  id: number       // member record ID
  userId: number   // user ID (used for add/remove operations)
  username: string
  fullName: string
  email?: string
  departmentName?: string
  role: 'ADMIN' | 'SECRETARY' | 'USER'
  meetingRole?: MeetingRole
  department?: Department
}

// ─── Recording ────────────────────────────────────────────────────────────────

export type RecordingStatus = 'RECORDING' | 'COMPLETED' | 'FAILED'

export interface Recording {
  id: number
  meetingId?: number
  fileName: string
  fileSize: number
  filePath?: string
  url?: string
  startTime: string
  endTime?: string
  status: RecordingStatus
  createdBy?: number
  createdByName?: string
  createdAt?: string
}

export interface MeetingMessage {
  id: number
  meetingId: number
  memberId: number
  userId?: number
  senderName: string
  meetingRole?: MeetingRole
  content: string
  createdAt: string
}

// ─── Document ─────────────────────────────────────────────────────────────────

export interface MeetingDocument {
  id: number
  fileName: string
  fileSize: number
  uploadedAt: string
  uploadedBy: MeetingUser
}

// ─── Attendance ───────────────────────────────────────────────────────────────

export interface AttendanceLog {
  id: number
  userId?: number
  userFullName?: string
  username?: string
  joinTime: string
  leaveTime?: string
  /** Duration in seconds from the backend */
  durationSeconds?: number
  ipAddress?: string
  deviceInfo?: string
}

// ─── Room availability ────────────────────────────────────────────────────────

export interface RoomAvailabilitySlot {
  startTime: string
  endTime: string
  meetingId: number
  meetingTitle: string
  meetingCode?: string
  status?: string
}

export interface RoomAvailabilityResponse {
  roomId: number
  roomName: string
  available: boolean
  bookedSlots: RoomAvailabilitySlot[]
}

// ─── Create/Update request bodies ────────────────────────────────────────────

export interface CreateMeetingRequest {
  title: string
  name?: string
  description?: string
  startTime: string
  endTime: string
  roomId: number
  departmentId: number
  hostUserId: number
  secretaryUserId: number
  transcriptionPriority?: TranscriptionPriority
}

export interface UpdateMeetingRequest {
  title?: string
  name?: string
  description?: string
  startTime?: string
  endTime?: string
  roomId?: number
  departmentId?: number
  hostUserId?: number
  secretaryUserId?: number
  transcriptionPriority?: TranscriptionPriority
}

// ─── Meeting list filters ─────────────────────────────────────────────────────

export interface MeetingFilters {
  startDate?: string
  endDate?: string
  roomId?: number
  departmentId?: number
  status?: MeetingStatus
  page?: number
  size?: number
  sort?: string
}
