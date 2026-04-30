/**
 * recordingService.ts — wraps recording-related API calls.
 * Requirements: 1.4
 */

import api from './api'
import type { ApiResponse } from '../types/api'
import type { Recording } from '../types/meeting'

/**
 * List recordings for a meeting.
 */
export async function listRecordings(
  meetingId: number,
): Promise<ApiResponse<Recording[]>> {
  const response = await api.get<ApiResponse<Recording[]>>(
    `/meetings/${meetingId}/recordings`,
  )
  return response.data
}

/**
 * Start recording a meeting. Host/Secretary only.
 */
export async function startRecording(
  meetingId: number,
): Promise<ApiResponse<Recording>> {
  const response = await api.post<ApiResponse<Recording>>(
    `/meetings/${meetingId}/recordings/start`,
  )
  return response.data
}

/**
 * Stop a recording. Host/Secretary only.
 */
export async function stopRecording(
  meetingId: number,
  recordingId: number,
): Promise<ApiResponse<Recording>> {
  const response = await api.post<ApiResponse<Recording>>(
    `/meetings/${meetingId}/recordings/${recordingId}/stop`,
  )
  return response.data
}

/**
 * Delete a recording. ADMIN only.
 */
export async function deleteRecording(recordingId: number): Promise<void> {
  await api.delete(`/recordings/${recordingId}`)
}

/**
 * Get the download URL for a recording.
 * Returns a blob URL for the file download.
 */
export async function downloadRecording(recordingId: number): Promise<Blob> {
  const response = await api.get<Blob>(`/recordings/${recordingId}/download`, {
    responseType: 'blob',
  })
  return response.data
}

/**
 * Trigger a browser download for a recording.
 */
export async function triggerRecordingDownload(
  recordingId: number,
  fileName: string,
): Promise<void> {
  const blob = await downloadRecording(recordingId)
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

/**
 * Format file size in human-readable form.
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`
}
