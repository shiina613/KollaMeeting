/**
 * Unit tests for useMeetingLifecycle hook.
 *
 * Tests: join on mount, leave on unmount, store initialization/cleanup, error handling.
 * Requirements: 13.4
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { useMeetingLifecycle } from '../useMeetingLifecycle'
import type { Meeting } from '../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

const mockSetActiveMeeting = vi.fn()
const mockClearActiveMeeting = vi.fn()
const mockSetParticipants = vi.fn()

vi.mock('../../services/meetingService', () => ({
  joinMeeting: vi.fn(() => Promise.resolve({ data: undefined, message: 'OK', status: 200 })),
  leaveMeeting: vi.fn(() => Promise.resolve({ data: undefined, message: 'OK', status: 200 })),
  getActiveParticipants: vi.fn(() =>
    Promise.resolve({
      data: [{ userId: 1, userFullName: 'Host User', username: 'host' }],
      message: 'OK',
      status: 200,
    }),
  ),
}))

vi.mock('../../store/meetingStore', () => ({
  default: () => ({
    setActiveMeeting: mockSetActiveMeeting,
    clearActiveMeeting: mockClearActiveMeeting,
    setParticipants: mockSetParticipants,
  }),
}))

import { joinMeeting, leaveMeeting, getActiveParticipants } from '../../services/meetingService'

// ─── Test Data ────────────────────────────────────────────────────────────────

const MOCK_MEETING: Meeting = {
  id: 101,
  title: 'Sprint Planning',
  meetingCode: 'SPR-101',
  mode: 'FREE_MODE',
  status: 'ACTIVE',
  startTime: '2024-01-15T09:00:00Z',
  endTime: '2024-01-15T10:00:00Z',
  hostId: 1,
  secretaryId: 2,
  roomId: 5,
  transcriptionPriority: 'HIGH_PRIORITY',
} as Meeting

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  mockSetActiveMeeting.mockClear()
  mockClearActiveMeeting.mockClear()
  mockSetParticipants.mockClear()
  vi.mocked(joinMeeting).mockClear()
  vi.mocked(leaveMeeting).mockClear()
  vi.mocked(getActiveParticipants).mockClear()
  // Reset to default resolved value
  vi.mocked(joinMeeting).mockResolvedValue({ data: undefined, message: 'OK', status: 200 })
  vi.mocked(leaveMeeting).mockResolvedValue({ data: undefined, message: 'OK', status: 200 })
  vi.mocked(getActiveParticipants).mockResolvedValue({
    data: [{ userId: 1, userFullName: 'Host User', username: 'host' }],
    message: 'OK',
    status: 200,
  })
})

afterEach(() => {
  // Do NOT use vi.restoreAllMocks() here — it removes mock implementations
  // before React cleanup functions run, causing "Cannot read properties of undefined"
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useMeetingLifecycle — mount behavior', () => {
  it('should set active meeting in store on mount', async () => {
    const { unmount } = renderHook(() => useMeetingLifecycle({ meeting: MOCK_MEETING }))

    expect(mockSetActiveMeeting).toHaveBeenCalledWith(MOCK_MEETING)

    await act(async () => { unmount() })
  })

  it('should call joinMeeting API on mount', async () => {
    const { unmount } = renderHook(() => useMeetingLifecycle({ meeting: MOCK_MEETING }))

    expect(joinMeeting).toHaveBeenCalledWith(MOCK_MEETING.id)

    await act(async () => { unmount() })
  })

  it('should load active participants after join', async () => {
    const { unmount } = renderHook(() => useMeetingLifecycle({ meeting: MOCK_MEETING }))

    await waitFor(() => {
      expect(getActiveParticipants).toHaveBeenCalledWith(MOCK_MEETING.id)
      expect(mockSetParticipants).toHaveBeenCalledWith([
        { userId: 1, userName: 'Host User', isConnected: true },
      ])
    })

    await act(async () => { unmount() })
  })

  it('should only call joinMeeting once even on re-render', async () => {
    const { rerender, unmount } = renderHook(() =>
      useMeetingLifecycle({ meeting: MOCK_MEETING }),
    )

    rerender()
    rerender()

    expect(joinMeeting).toHaveBeenCalledTimes(1)

    await act(async () => { unmount() })
  })
})

describe('useMeetingLifecycle — unmount behavior', () => {
  it('should call leaveMeeting API on unmount', async () => {
    const { unmount } = renderHook(() =>
      useMeetingLifecycle({ meeting: MOCK_MEETING }),
    )

    await act(async () => { unmount() })

    expect(leaveMeeting).toHaveBeenCalledWith(MOCK_MEETING.id)
  })

  it('should clear active meeting in store on unmount', async () => {
    const { unmount } = renderHook(() =>
      useMeetingLifecycle({ meeting: MOCK_MEETING }),
    )

    await act(async () => { unmount() })

    expect(mockClearActiveMeeting).toHaveBeenCalled()
  })
})

describe('useMeetingLifecycle — error handling', () => {
  it('should call onJoinError when joinMeeting fails', async () => {
    vi.mocked(joinMeeting).mockRejectedValue(new Error('Network error'))
    const onJoinError = vi.fn()

    const { unmount } = renderHook(() =>
      useMeetingLifecycle({ meeting: MOCK_MEETING, onJoinError }),
    )

    // Wait for the promise rejection to be handled
    await waitFor(() => {
      expect(onJoinError).toHaveBeenCalledWith('Không thể ghi nhận tham gia cuộc họp.')
    })

    await act(async () => { unmount() })
  })

  it('should not throw when onJoinError is not provided and join fails', async () => {
    vi.mocked(joinMeeting).mockRejectedValue(new Error('Network error'))

    // Should not throw
    const { unmount } = renderHook(() => useMeetingLifecycle({ meeting: MOCK_MEETING }))

    // Wait for the async rejection to be processed
    await act(async () => {
      await new Promise((r) => setTimeout(r, 0))
    })

    await act(async () => { unmount() })
  })

  it('should not throw when leaveMeeting fails on unmount', async () => {
    vi.mocked(leaveMeeting).mockRejectedValue(new Error('Network error'))

    const { unmount } = renderHook(() =>
      useMeetingLifecycle({ meeting: MOCK_MEETING }),
    )

    // Should not throw
    await act(async () => { unmount() })
  })
})
