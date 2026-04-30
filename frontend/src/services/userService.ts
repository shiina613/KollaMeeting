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
 * List users eligible to be a host (ADMIN or SECRETARY role).
 */
export async function listHostCandidates(): Promise<MeetingUser[]> {
  // Fetch ADMIN users
  const adminRes = await listUsers({ role: 'ADMIN', size: 100 })
  const secretaryRes = await listUsers({ role: 'SECRETARY', size: 100 })

  const admins = adminRes.data?.content ?? []
  const secretaries = secretaryRes.data?.content ?? []

  // Deduplicate by id
  const seen = new Set<number>()
  const combined: MeetingUser[] = []
  for (const u of [...admins, ...secretaries]) {
    if (!seen.has(u.id)) {
      seen.add(u.id)
      combined.push(u)
    }
  }
  return combined
}

/**
 * List users eligible to be a secretary (SECRETARY role only).
 */
export async function listSecretaryCandidates(): Promise<MeetingUser[]> {
  const res = await listUsers({ role: 'SECRETARY', size: 100 })
  return res.data?.content ?? []
}
