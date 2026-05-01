/**
 * searchService.ts — search meetings and transcriptions.
 * Requirements: 13.1–13.7
 */

import api from './api'
import type { ApiResponse, PageResponse } from '../types/api'
import type { Meeting } from '../types/meeting'

export interface MeetingSearchFilters {
  query?: string
  startDate?: string
  endDate?: string
  roomId?: number
  departmentId?: number
  page?: number
  size?: number
}

export interface TranscriptionSearchResult {
  jobId: string
  meetingId: number
  meetingTitle: string
  speakerName: string
  text: string
  segmentStartTime: string
}

export interface TranscriptionSearchFilters {
  query: string
  page?: number
  size?: number
}

/**
 * Search meetings by date range, room, department, and text query.
 * Requirements: 13.2, 13.5
 */
export async function searchMeetings(
  filters: MeetingSearchFilters,
): Promise<ApiResponse<PageResponse<Meeting>>> {
  const params: Record<string, string | number | undefined> = {}
  if (filters.query) params.query = filters.query
  if (filters.startDate) params.startDate = filters.startDate
  if (filters.endDate) params.endDate = filters.endDate
  if (filters.roomId !== undefined) params.roomId = filters.roomId
  if (filters.departmentId !== undefined) params.departmentId = filters.departmentId
  if (filters.page !== undefined) params.page = filters.page
  if (filters.size !== undefined) params.size = filters.size

  const response = await api.get<ApiResponse<PageResponse<Meeting>>>('/search/meetings', { params })
  return response.data
}

/**
 * Full-text search in transcription segments.
 * Requirements: 13.4, 13.6
 */
export async function searchTranscriptions(
  filters: TranscriptionSearchFilters,
): Promise<ApiResponse<PageResponse<TranscriptionSearchResult>>> {
  const params: Record<string, string | number | undefined> = {
    query: filters.query,
  }
  if (filters.page !== undefined) params.page = filters.page
  if (filters.size !== undefined) params.size = filters.size

  const response = await api.get<ApiResponse<PageResponse<TranscriptionSearchResult>>>(
    '/search/transcriptions',
    { params },
  )
  return response.data
}
