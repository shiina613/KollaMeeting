/**
 * roomService.ts — wraps all room-related API calls.
 * Requirements: 12.1–12.8
 */

import api from './api'
import type { ApiResponse } from '../types/api'

export interface RoomDto {
  id: number
  name: string
  capacity?: number
  departmentId?: number
  departmentName?: string
  createdAt?: string
  updatedAt?: string
}

export interface CreateRoomRequest {
  name: string
  capacity?: number
  departmentId: number
}

export interface UpdateRoomRequest {
  name?: string
  capacity?: number
  departmentId?: number
}

export async function listRooms(departmentId?: number): Promise<ApiResponse<RoomDto[]>> {
  const params = departmentId !== undefined ? { departmentId } : {}
  const res = await api.get<ApiResponse<RoomDto[]>>('/rooms', { params })
  return res.data
}

export async function getRoom(id: number): Promise<ApiResponse<RoomDto>> {
  const res = await api.get<ApiResponse<RoomDto>>(`/rooms/${id}`)
  return res.data
}

export async function createRoom(data: CreateRoomRequest): Promise<ApiResponse<RoomDto>> {
  const res = await api.post<ApiResponse<RoomDto>>('/rooms', data)
  return res.data
}

export async function updateRoom(
  id: number,
  data: UpdateRoomRequest,
): Promise<ApiResponse<RoomDto>> {
  const res = await api.put<ApiResponse<RoomDto>>(`/rooms/${id}`, data)
  return res.data
}

export async function deleteRoom(id: number): Promise<void> {
  await api.delete(`/rooms/${id}`)
}
