/**
 * storageService.ts — storage stats and bulk delete API calls.
 * Requirements: 6.7
 */

import api from './api'
import type { ApiResponse } from '../types/api'

export interface StorageTypeStats {
  type: string
  count: number
  totalSize: number
}

export interface StorageStats {
  totalSize: number
  breakdown: StorageTypeStats[]
}

export interface BulkDeleteRequest {
  fileType?: string
  olderThanDays?: number
  meetingId?: number
}

export interface BulkDeleteResult {
  deletedCount: number
  freedBytes: number
}

/**
 * Get storage statistics.
 */
export async function getStorageStats(): Promise<ApiResponse<StorageStats>> {
  const response = await api.get<ApiResponse<StorageStats>>('/storage/stats')
  return response.data
}

/**
 * Bulk delete files by criteria.
 */
export async function bulkDelete(
  request: BulkDeleteRequest,
): Promise<ApiResponse<BulkDeleteResult>> {
  const response = await api.post<ApiResponse<BulkDeleteResult>>('/storage/bulk-delete', request)
  return response.data
}
