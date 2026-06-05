/**
 * Unit tests for jaasService.ts
 * Requirements: 4.1
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import api from '../api'
import { fetchJaasToken } from '../jaasService'
import type { ApiResponse } from '../../types/api'
import type { JaasTokenResponse } from '../jaasService'

// ─── Setup ────────────────────────────────────────────────────────────────────

const mockAxios = new MockAdapter(api)

beforeEach(() => {
  mockAxios.reset()
})

afterEach(() => {
  mockAxios.reset()
})

// ─── fetchJaasToken ───────────────────────────────────────────────────────────

describe('fetchJaasToken', () => {
  it('should call the correct URL format /meetings/{id}/jaas-token', async () => {
    const meetingId = 42
    const mockResponse: ApiResponse<JaasTokenResponse> = {
      success: true,
      data: {
        token: 'eyJhbGciOiJSUzI1NiJ9.test.signature',
        roomName: 'vpaas-magic-cookie-abc123/TESTCODE1234567890AB',
      },
    }

    mockAxios.onGet(`/meetings/${meetingId}/jaas-token`).reply(200, mockResponse)

    await fetchJaasToken(meetingId)

    expect(mockAxios.history.get).toHaveLength(1)
    expect(mockAxios.history.get[0].url).toBe(`/meetings/${meetingId}/jaas-token`)
  })

  it('should return { token, roomName } from the response', async () => {
    const meetingId = 7
    const expectedToken = 'eyJhbGciOiJSUzI1NiJ9.payload.sig'
    const expectedRoomName = 'vpaas-magic-cookie-abc123/MEETINGCODE12345678'

    const mockResponse: ApiResponse<JaasTokenResponse> = {
      success: true,
      data: {
        token: expectedToken,
        roomName: expectedRoomName,
      },
    }

    mockAxios.onGet(`/meetings/${meetingId}/jaas-token`).reply(200, mockResponse)

    const result = await fetchJaasToken(meetingId)

    expect(result.token).toBe(expectedToken)
    expect(result.roomName).toBe(expectedRoomName)
  })

  it('should use the meetingId in the URL path', async () => {
    const meetingIds = [1, 99, 1000]

    for (const id of meetingIds) {
      mockAxios.reset()
      const mockResponse: ApiResponse<JaasTokenResponse> = {
        success: true,
        data: { token: `token-for-${id}`, roomName: `appid/room-${id}` },
      }

      mockAxios.onGet(`/meetings/${id}/jaas-token`).reply(200, mockResponse)

      const result = await fetchJaasToken(id)

      expect(mockAxios.history.get[0].url).toBe(`/meetings/${id}/jaas-token`)
      expect(result.token).toBe(`token-for-${id}`)
      expect(result.roomName).toBe(`appid/room-${id}`)
    }
  })

  it('should propagate errors when the request fails', async () => {
    const meetingId = 5

    mockAxios.onGet(`/meetings/${meetingId}/jaas-token`).reply(403, {
      success: false,
      message: 'User is not a member of this meeting',
    })

    await expect(fetchJaasToken(meetingId)).rejects.toThrow()
  })

  it('should propagate 404 errors when meeting does not exist', async () => {
    const meetingId = 9999

    mockAxios.onGet(`/meetings/${meetingId}/jaas-token`).reply(404, {
      success: false,
      message: 'Meeting not found: 9999',
    })

    await expect(fetchJaasToken(meetingId)).rejects.toThrow()
  })
})
