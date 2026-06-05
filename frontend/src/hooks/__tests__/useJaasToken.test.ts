/**
 * Unit tests for useJaasToken hook.
 *
 * Tests: token fetching, refresh scheduling, error handling, retry, cleanup.
 * Requirements: 13.3
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { useJaasToken } from '../useJaasToken'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../../services/jaasService', () => ({
  fetchJaasToken: vi.fn(),
}))

import { fetchJaasToken } from '../../services/jaasService'

// ─── Constants ────────────────────────────────────────────────────────────────

const MEETING_ID = 42
const MOCK_TOKEN = 'eyJhbGciOiJSUzI1NiJ9.mock-token'
const MOCK_ROOM_NAME = 'vpaas-magic-cookie-abc123/meeting-42'

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useJaasToken — initial fetch', () => {
  it('should fetch token on mount and return token and roomName', async () => {
    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: MOCK_TOKEN,
      roomName: MOCK_ROOM_NAME,
    })

    const { result } = renderHook(() => useJaasToken(MEETING_ID))

    // Initially loading
    expect(result.current.isLoading).toBe(true)
    expect(result.current.token).toBeNull()
    expect(result.current.roomName).toBeNull()
    expect(result.current.error).toBeNull()

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.token).toBe(MOCK_TOKEN)
    expect(result.current.roomName).toBe(MOCK_ROOM_NAME)
    expect(result.current.error).toBeNull()
    expect(fetchJaasToken).toHaveBeenCalledWith(MEETING_ID)
  })

  it('should set error state when fetch fails', async () => {
    vi.mocked(fetchJaasToken).mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useJaasToken(MEETING_ID))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.token).toBeNull()
    expect(result.current.roomName).toBeNull()
    expect(result.current.error).toBe('Không thể lấy token JaaS. Vui lòng thử lại.')
  })

  it('should not fetch when JaaS is not configured', async () => {
    vi.unstubAllEnvs()
    vi.stubEnv('VITE_JAAS_APP_ID', '')

    const { result } = renderHook(() => useJaasToken(MEETING_ID))

    // Should not be loading and should not call fetchJaasToken
    expect(result.current.isLoading).toBe(false)
    expect(result.current.token).toBeNull()
    expect(result.current.roomName).toBeNull()
    expect(result.current.error).toBeNull()
    expect(fetchJaasToken).not.toHaveBeenCalled()
  })
})

describe('useJaasToken — refresh scheduling', () => {
  it('should schedule a refresh at 55 minutes after successful fetch', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })

    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: MOCK_TOKEN,
      roomName: MOCK_ROOM_NAME,
    })

    const { result } = renderHook(() => useJaasToken(MEETING_ID))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(fetchJaasToken).toHaveBeenCalledTimes(1)

    // Provide a new token for the refresh
    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: 'refreshed-token',
      roomName: MOCK_ROOM_NAME,
    })

    // Advance time by 55 minutes
    await act(async () => {
      vi.advanceTimersByTime(55 * 60 * 1000)
    })

    await waitFor(() => {
      expect(fetchJaasToken).toHaveBeenCalledTimes(2)
    })

    await waitFor(() => {
      expect(result.current.token).toBe('refreshed-token')
    })
  })

  it('should not schedule refresh when fetch fails', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })

    vi.mocked(fetchJaasToken).mockRejectedValue(new Error('fail'))

    const { result } = renderHook(() => useJaasToken(MEETING_ID))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    // Advance time — no refresh should be scheduled
    await act(async () => {
      vi.advanceTimersByTime(55 * 60 * 1000)
    })

    expect(fetchJaasToken).toHaveBeenCalledTimes(1)
  })
})

describe('useJaasToken — retry', () => {
  it('should retry fetching token when retry is called', async () => {
    vi.mocked(fetchJaasToken).mockRejectedValueOnce(new Error('fail'))

    const { result } = renderHook(() => useJaasToken(MEETING_ID))

    await waitFor(() => {
      expect(result.current.error).not.toBeNull()
    })

    // Now mock a successful response for retry
    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: MOCK_TOKEN,
      roomName: MOCK_ROOM_NAME,
    })

    act(() => {
      result.current.retry()
    })

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    expect(result.current.token).toBe(MOCK_TOKEN)
    expect(result.current.roomName).toBe(MOCK_ROOM_NAME)
    expect(result.current.error).toBeNull()
  })

  it('should clear existing refresh timer before retrying', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })

    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: MOCK_TOKEN,
      roomName: MOCK_ROOM_NAME,
    })

    const { result } = renderHook(() => useJaasToken(MEETING_ID))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    // Retry immediately (should clear the scheduled refresh)
    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: 'retry-token',
      roomName: MOCK_ROOM_NAME,
    })

    act(() => {
      result.current.retry()
    })

    await waitFor(() => {
      expect(result.current.token).toBe('retry-token')
    })

    expect(fetchJaasToken).toHaveBeenCalledTimes(2)
  })
})

describe('useJaasToken — cleanup', () => {
  it('should clear refresh timer on unmount', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })

    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: MOCK_TOKEN,
      roomName: MOCK_ROOM_NAME,
    })

    const { result, unmount } = renderHook(() => useJaasToken(MEETING_ID))

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false)
    })

    unmount()

    // Advance time — the refresh should NOT fire since we unmounted
    await act(async () => {
      vi.advanceTimersByTime(55 * 60 * 1000)
    })

    // Only the initial fetch should have been called
    expect(fetchJaasToken).toHaveBeenCalledTimes(1)
  })
})
