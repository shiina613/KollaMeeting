/**
 * jaasService.ts — wraps the JaaS token API call.
 * Uses the shared Axios instance with JWT interceptor.
 * Requirements: 4.1
 */

import api from './api'
import type { ApiResponse } from '../types/api'

export interface JaasTokenResponse {
  token: string
  roomName: string
}

/**
 * Fetch a JaaS JWT token for the given meeting.
 * The backend verifies membership and signs the token with the JaaS private key.
 * Requirements: 4.1
 */
export async function fetchJaasToken(meetingId: number): Promise<JaasTokenResponse> {
  const response = await api.get<ApiResponse<JaasTokenResponse>>(
    `/meetings/${meetingId}/jaas-token`,
  )
  return response.data.data
}
