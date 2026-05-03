/**
 * departmentService.ts — wraps all department-related API calls.
 * Requirements: 12.1–12.8
 */

import api from './api'
import type { ApiResponse } from '../types/api'

export interface DepartmentDto {
  id: number
  name: string
  description?: string
  createdAt?: string
  updatedAt?: string
}

export interface CreateDepartmentRequest {
  name: string
  description?: string
}

export interface UpdateDepartmentRequest {
  name?: string
  description?: string
}

export async function listDepartments(): Promise<ApiResponse<DepartmentDto[]>> {
  const res = await api.get<ApiResponse<DepartmentDto[]>>('/departments')
  return res.data
}

export async function getDepartment(id: number): Promise<ApiResponse<DepartmentDto>> {
  const res = await api.get<ApiResponse<DepartmentDto>>(`/departments/${id}`)
  return res.data
}

export async function createDepartment(
  data: CreateDepartmentRequest,
): Promise<ApiResponse<DepartmentDto>> {
  const res = await api.post<ApiResponse<DepartmentDto>>('/departments', data)
  return res.data
}

export async function updateDepartment(
  id: number,
  data: UpdateDepartmentRequest,
): Promise<ApiResponse<DepartmentDto>> {
  const res = await api.put<ApiResponse<DepartmentDto>>(`/departments/${id}`, data)
  return res.data
}

export async function deleteDepartment(id: number): Promise<void> {
  await api.delete(`/departments/${id}`)
}
