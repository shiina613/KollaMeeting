/**
 * meetingService.ts — wraps all meeting-related API calls.
 * Uses the shared Axios instance with JWT interceptor.
 * Requirements: 1.4
 */

import api from './api'
import type { AxiosError } from 'axios'
import type { ApiResponse, PageResponse } from '../types/api'
import type {
  Meeting,
  MeetingMember,
  MeetingFilters,
  CreateMeetingRequest,
  UpdateMeetingRequest,
  Room,
  Department,
  RoomAvailabilitySlot,
  RaiseHandRequest,
  SpeakingPermission,
} from '../types/meeting'

// ─── Meetings ─────────────────────────────────────────────────────────────────

/**
 * List meetings with optional filters and pagination.
 */
export async function listMeetings(
  filters: MeetingFilters = {},
): Promise<ApiResponse<PageResponse<Meeting>>> {
  const params: Record<string, string | number | undefined> = {}

  if (filters.startDate) params.startDate = filters.startDate
  if (filters.endDate) params.endDate = filters.endDate
  if (filters.roomId !== undefined) params.roomId = filters.roomId
  if (filters.departmentId !== undefined) params.departmentId = filters.departmentId
  if (filters.status) params.status = filters.status
  if (filters.page !== undefined) params.page = filters.page
  if (filters.size !== undefined) params.size = filters.size
  if (filters.sort) params.sort = filters.sort

  const response = await api.get<ApiResponse<PageResponse<Meeting>>>('/meetings', { params })
  return response.data
}

/**
 * Get a single meeting by ID.
 */
export async function getMeeting(id: number): Promise<ApiResponse<Meeting>> {
  const response = await api.get<ApiResponse<Meeting>>(`/meetings/${id}`)
  return response.data
}

/**
 * Create a new meeting.
 */
export async function createMeeting(
  data: CreateMeetingRequest,
): Promise<ApiResponse<Meeting>> {
  const response = await api.post<ApiResponse<Meeting>>('/meetings', data)
  return response.data
}

/**
 * Update an existing meeting.
 */
export async function updateMeeting(
  id: number,
  data: UpdateMeetingRequest,
): Promise<ApiResponse<Meeting>> {
  const response = await api.put<ApiResponse<Meeting>>(`/meetings/${id}`, data)
  return response.data
}

/**
 * Delete a meeting.
 */
export async function deleteMeeting(id: number): Promise<void> {
  await api.delete(`/meetings/${id}`)
}

// ─── Meeting members ──────────────────────────────────────────────────────────

/**
 * List members of a meeting.
 */
export async function listMeetingMembers(
  meetingId: number,
): Promise<ApiResponse<MeetingMember[]>> {
  const response = await api.get<ApiResponse<MeetingMember[]>>(
    `/meetings/${meetingId}/members`,
  )
  return response.data
}

/**
 * Add a member to a meeting.
 */
export async function addMeetingMember(
  meetingId: number,
  userId: number,
): Promise<ApiResponse<void>> {
  const response = await api.post<ApiResponse<void>>(
    `/meetings/${meetingId}/members`,
    { userId },
  )
  return response.data
}

/**
 * Remove a member from a meeting.
 */
export async function removeMeetingMember(
  meetingId: number,
  userId: number,
): Promise<void> {
  await api.delete(`/meetings/${meetingId}/members/${userId}`)
}

// ─── Meeting lifecycle ────────────────────────────────────────────────────────

/**
 * Join a meeting (creates attendance log entry).
 */
export async function joinMeeting(id: number): Promise<ApiResponse<void>> {
  const response = await api.post<ApiResponse<void>>(`/meetings/${id}/join`)
  return response.data
}

/**
 * Leave a meeting.
 */
export async function leaveMeeting(id: number): Promise<ApiResponse<void>> {
  const response = await api.post<ApiResponse<void>>(`/meetings/${id}/leave`)
  return response.data
}

/**
 * Activate a meeting (SCHEDULED → ACTIVE). Host only.
 */
export async function activateMeeting(id: number): Promise<ApiResponse<Meeting>> {
  const response = await api.post<ApiResponse<Meeting>>(`/meetings/${id}/activate`)
  return response.data
}

/**
 * End a meeting (ACTIVE → ENDED). Host/Secretary only.
 */
export async function endMeeting(id: number): Promise<ApiResponse<Meeting>> {
  const response = await api.post<ApiResponse<Meeting>>(`/meetings/${id}/end`)
  return response.data
}

/**
 * Get attendance logs for a meeting.
 */
export async function getMeetingAttendance(
  meetingId: number,
): Promise<ApiResponse<import('../types/meeting').AttendanceLog[]>> {
  const response = await api.get<ApiResponse<import('../types/meeting').AttendanceLog[]>>(
    `/meetings/${meetingId}/attendance`,
  )
  return response.data
}

// ─── Rooms ────────────────────────────────────────────────────────────────────

/**
 * List all rooms.
 */
export async function listRooms(): Promise<ApiResponse<Room[]>> {
  const response = await api.get<ApiResponse<Room[]>>('/rooms')
  return response.data
}

/**
 * Get room availability slots for a given time range.
 * Returns null if the request fails (room is considered unavailable).
 */
export async function getRoomAvailability(
  roomId: number,
  startTime: string,
  endTime: string,
): Promise<RoomAvailabilitySlot[]> {
  try {
    const response = await api.get<ApiResponse<RoomAvailabilitySlot[]>>(
      `/rooms/${roomId}/availability`,
      { params: { startTime, endTime } },
    )
    return response.data.data ?? []
  } catch {
    return []
  }
}

// ─── Departments ──────────────────────────────────────────────────────────────

/**
 * List all departments.
 */
export async function listDepartments(): Promise<ApiResponse<Department[]>> {
  const response = await api.get<ApiResponse<Department[]>>('/departments')
  return response.data
}

// ─── Conflict detection ───────────────────────────────────────────────────────

/**
 * Check if a 409 AxiosError is a scheduling conflict.
 */
export function isSchedulingConflict(error: unknown): boolean {
  const axiosError = error as AxiosError
  return axiosError?.response?.status === 409
}

/**
 * Extract conflict message from a 409 error response.
 */
export function getConflictMessage(error: unknown): string {
  const axiosError = error as AxiosError<{ message?: string }>
  return (
    axiosError?.response?.data?.message ??
    'Phòng họp đã được đặt trong khoảng thời gian này.'
  )
}

// ─── Raise Hand ───────────────────────────────────────────────────────────────

/**
 * Submit a raise-hand request (Participant action).
 * Requirements: 22.2
 */
export async function raiseHand(meetingId: number): Promise<ApiResponse<void>> {
  const response = await api.post<ApiResponse<void>>(
    `/meetings/${meetingId}/raise-hand`,
  )
  return response.data
}

/**
 * Lower hand / cancel raise-hand request (Participant action).
 * Requirements: 22.7
 */
export async function lowerHand(meetingId: number): Promise<ApiResponse<void>> {
  const response = await api.delete<ApiResponse<void>>(
    `/meetings/${meetingId}/raise-hand`,
  )
  return response.data
}

/**
 * List pending raise-hand requests for a meeting (Host view).
 * Returns requests in chronological order (oldest first).
 * Requirements: 22.9
 */
export async function listRaiseHandRequests(
  meetingId: number,
): Promise<ApiResponse<RaiseHandRequest[]>> {
  const response = await api.get<ApiResponse<RaiseHandRequest[]>>(
    `/meetings/${meetingId}/raise-hand`,
  )
  return response.data
}

// ─── Speaking Permission ──────────────────────────────────────────────────────

/**
 * Grant speaking permission to a participant (Host action).
 * Requirements: 22.4
 */
export async function grantSpeakingPermission(
  meetingId: number,
  userId: number,
): Promise<ApiResponse<void>> {
  const response = await api.post<ApiResponse<void>>(
    `/meetings/${meetingId}/speaking-permission/${userId}`,
  )
  return response.data
}

/**
 * Revoke speaking permission from the current speaker (Host action).
 * Requirements: 22.6
 */
export async function revokeSpeakingPermission(
  meetingId: number,
): Promise<ApiResponse<void>> {
  const response = await api.delete<ApiResponse<void>>(
    `/meetings/${meetingId}/speaking-permission`,
  )
  return response.data
}

/**
 * Get the current speaking permission for a meeting.
 * Requirements: 22.5
 */
export async function getSpeakingPermission(
  meetingId: number,
): Promise<ApiResponse<SpeakingPermission | null>> {
  const response = await api.get<ApiResponse<SpeakingPermission | null>>(
    `/meetings/${meetingId}/speaking-permission`,
  )
  return response.data
}
