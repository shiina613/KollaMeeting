/**
 * minutesService.ts — wraps all Minutes-related API calls.
 * Uses the shared Axios instance with JWT interceptor.
 * Requirements: 25.4–25.6
 */

import api from './api'
import useAuthStore from '../store/authStore'
import type { ApiResponse } from '../types/api'
import type { Minutes, MinutesVersion } from '../types/minutes'

// ─── Get minutes info ─────────────────────────────────────────────────────────

/**
 * Get minutes info for a meeting.
 * GET /api/v1/meetings/{id}/minutes
 * Requirements: 25.1
 */
export async function getMinutes(meetingId: number): Promise<ApiResponse<Minutes>> {
  const response = await api.get<ApiResponse<Minutes>>(`/meetings/${meetingId}/minutes`)
  return response.data
}

// ─── Host confirm ─────────────────────────────────────────────────────────────

/**
 * Host confirms the draft minutes with a digital stamp.
 * POST /api/v1/meetings/{id}/minutes/confirm
 * Requirements: 25.4
 */
export async function confirmMinutes(meetingId: number): Promise<ApiResponse<Minutes>> {
  const response = await api.post<ApiResponse<Minutes>>(
    `/meetings/${meetingId}/minutes/confirm`,
  )
  return response.data
}

// ─── Secretary edit ───────────────────────────────────────────────────────────

/**
 * Secretary edits the minutes HTML content.
 * PUT /api/v1/meetings/{id}/minutes/edit
 * Requirements: 25.5
 */
export async function editMinutes(
  meetingId: number,
  contentHtml: string,
): Promise<ApiResponse<Minutes>> {
  const response = await api.put<ApiResponse<Minutes>>(
    `/meetings/${meetingId}/minutes/edit`,
    { contentHtml },
  )
  return response.data
}

// ─── Download PDF ─────────────────────────────────────────────────────────────

/**
 * Build the authenticated download URL for a minutes PDF version.
 * Returns the URL string (with auth token as query param for iframe src usage).
 * Requirements: 25.6
 */
export function getMinutesDownloadUrl(
  meetingId: number,
  version: MinutesVersion,
): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'
  const token = useAuthStore.getState().token
  const tokenParam = token ? `&token=${encodeURIComponent(token)}` : ''
  return `${baseUrl}/meetings/${meetingId}/minutes/download?version=${version}${tokenParam}`
}

/**
 * Trigger a browser file download for a minutes PDF version.
 * Uses blob URL to support auth header injection.
 * Requirements: 25.6
 */
export async function downloadMinutesPdf(
  meetingId: number,
  version: MinutesVersion,
): Promise<void> {
  const response = await api.get(
    `/meetings/${meetingId}/minutes/download`,
    {
      params: { version },
      responseType: 'blob',
    },
  )

  const blob = new Blob([response.data as BlobPart], { type: 'application/pdf' })
  const url = URL.createObjectURL(blob)

  const versionLabels: Record<MinutesVersion, string> = {
    draft: 'ban-nhap',
    confirmed: 'ban-xac-nhan',
    secretary: 'ban-thu-ky',
  }

  const link = document.createElement('a')
  link.href = url
  link.download = `bien-ban-${versionLabels[version]}-${meetingId}.pdf`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
