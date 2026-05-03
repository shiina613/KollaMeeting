/**
 * userService.ts — wraps user-related API calls.
 * Requirements: 1.4
 */

import api from './api'
import type { ApiResponse, PageResponse } from '../types/api'
import type { MeetingUser } from '../types/meeting'

export interface UserFilters {
  page?: number
  size?: number
  role?: 'ADMIN' | 'SECRETARY' | 'USER'
  departmentId?: number
  search?: string
}

export interface CreateUserRequest {
  username: string
  email: string
  fullName: string
  password: string
  role: 'ADMIN' | 'SECRETARY' | 'USER'
  departmentId?: number
}

export interface UpdateUserRequest {
  email?: string
  fullName?: string
  role?: 'ADMIN' | 'SECRETARY' | 'USER'
  departmentId?: number
  isActive?: boolean
}

/**
 * List users with optional filters and pagination.
 */
export async function listUsers(
  filters: UserFilters = {},
): Promise<ApiResponse<PageResponse<MeetingUser>>> {
  const params: Record<string, string | number | undefined> = {}

  if (filters.page !== undefined) params.page = filters.page
  if (filters.size !== undefined) params.size = filters.size
  if (filters.role) params.role = filters.role
  if (filters.departmentId !== undefined) params.departmentId = filters.departmentId
  if (filters.search) params.search = filters.search

  const response = await api.get<ApiResponse<PageResponse<MeetingUser>>>('/users', {
    params,
  })
  return response.data
}

/**
 * Get the current authenticated user.
 */
export async function getCurrentUser(): Promise<ApiResponse<MeetingUser>> {
  const response = await api.get<ApiResponse<MeetingUser>>('/users/me')
  return response.data
}

/**
 * Get a user by ID.
 */
export async function getUser(id: number): Promise<ApiResponse<MeetingUser>> {
  const response = await api.get<ApiResponse<MeetingUser>>(`/users/${id}`)
  return response.data
}

/**
 * Create a new user. ADMIN only.
 */
export async function createUser(
  data: CreateUserRequest,
): Promise<ApiResponse<MeetingUser>> {
  const response = await api.post<ApiResponse<MeetingUser>>('/users', data)
  return response.data
}

/**
 * Update a user. ADMIN or self.
 */
export async function updateUser(
  id: number,
  data: UpdateUserRequest,
): Promise<ApiResponse<MeetingUser>> {
  const response = await api.put<ApiResponse<MeetingUser>>(`/users/${id}`, data)
  return response.data
}

/**
 * Toggle a user's active/inactive status. ADMIN only.
 */
export async function toggleUserActive(id: number): Promise<ApiResponse<MeetingUser>> {
  const response = await api.patch<ApiResponse<MeetingUser>>(`/users/${id}/toggle-active`)
  return response.data
}

/**
 * Delete a user. ADMIN only.
 */
export async function deleteUser(id: number): Promise<void> {
  await api.delete(`/users/${id}`)
}

/**
 * Reset a user's password. ADMIN only.
 */
export async function resetPassword(
  id: number,
  newPassword: string,
): Promise<ApiResponse<void>> {
  const response = await api.post<ApiResponse<void>>(`/users/${id}/reset-password`, {
    newPassword,
  })
  return response.data
}

/**
 * List users eligible to be a host or secretary (SECRETARY role, active).
 * Uses the /users/candidates endpoint accessible by any authenticated user.
 */
async function listCandidates(): Promise<MeetingUser[]> {
  const res = await api.get<ApiResponse<MeetingUser[]>>('/users/candidates')
  return res.data.data ?? []
}

/**
 * Search active users by name or username (partial match).
 * Accessible by any authenticated user. Used to add meeting members.
 */
export async function searchUsers(q: string): Promise<MeetingUser[]> {
  if (!q.trim()) return []
  const res = await api.get<ApiResponse<MeetingUser[]>>('/users/search', { params: { q } })
  return res.data.data ?? []
}

/**
 * List all active users ordered by full name.
 * Accessible by any authenticated user. Used to populate member picker.
 */
export async function listAllActiveUsers(): Promise<MeetingUser[]> {
  const res = await api.get<ApiResponse<MeetingUser[]>>('/users/active')
  return res.data.data ?? []
}

/**
 * List users eligible to be a host (SECRETARY role only).
 */
export async function listHostCandidates(): Promise<MeetingUser[]> {
  return listCandidates()
}

/**
 * List users eligible to be a secretary (SECRETARY role only).
 */
export async function listSecretaryCandidates(): Promise<MeetingUser[]> {
  return listCandidates()
}
