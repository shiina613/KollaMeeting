/**
 * Unit tests for useMeetingAudioCapture hook.
 *
 * Tests: mode-aware capture start/stop, speaker turn ID management,
 * host/secretary auto-revoke, permission denied state.
 * Requirements: 13.5
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useMeetingAudioCapture } from '../useMeetingAudioCapture'
import type { Meeting } from '../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

const mockStartCapture = vi.fn(() => Promise.resolve())
const mockStopCapture = vi.fn()

vi.mock('../useAudioCapture', () => ({
  default: vi.fn((opts: { meetingId: number; speakerTurnId: string | null }) => ({
    startCapture: mockStartCapture,
    stopCapture: mockStopCapture,
    isCapturing: false,
    isPermissionDenied: false,
    status: 'idle',
  })),
  useAudioCapture: vi.fn((opts: { meetingId: number; speakerTurnId: string | null }) => ({
    startCapture: mockStartCapture,
    stopCapture: mockStopCapture,
    isCapturing: false,
    isPermissionDenied: false,
    status: 'idle',
  })),
}))

const mockRevokeSpeakingPermission = vi.fn(() => Promise.resolve({ data: undefined, message: 'OK', status: 200 }))

vi.mock('../../services/meetingService', () => ({
  revokeSpeakingPermission: (...args: unknown[]) => mockRevokeSpeakingPermission(...args),
}))

// Mock the meeting store
let mockMode = 'MEETING_MODE'
let mockSpeakingPermission: { userId: number; userName: string; speakerTurnId: string; grantedAt: string } | null = null

vi.mock('../../store/meetingStore', () => ({
  default: Object.assign(
    () => ({ mode: mockMode }),
    {
      getState: () => ({
        mode: mockMode,
        speakingPermission: mockSpeakingPermission,
      }),
    },
  ),
}))

// ─── Test Data ────────────────────────────────────────────────────────────────

const MOCK_MEETING: Meeting = {
  id: 42,
  title: 'Test Meeting',
  meetingCode: 'TST-042',
  mode: 'MEETING_MODE',
  status: 'ACTIVE',
  startTime: '2024-01-15T09:00:00Z',
  endTime: '2024-01-15T10:00:00Z',
  hostId: 1,
  secretaryId: 2,
  roomId: 5,
  transcriptionPriority: 'HIGH_PRIORITY',
} as Meeting

const mockJitsiRef = { current: null }

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  mockStartCapture.mockClear()
  mockStopCapture.mockClear()
  mockRevokeSpeakingPermission.mockClear()
  mockMode = 'MEETING_MODE'
  mockSpeakingPermission = null
})

afterEach(() => {
  vi.clearAllMocks()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useMeetingAudioCapture — MEETING_MODE behavior', () => {
  it('should start capture when mic is unmuted in MEETING_MODE', () => {
    mockMode = 'MEETING_MODE'

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(false)
    })

    expect(mockStartCapture).toHaveBeenCalledTimes(1)
    // Should be called with a turn ID string
    expect(mockStartCapture).toHaveBeenCalledWith(expect.stringContaining('42-'))
  })

  it('should stop capture when mic is muted in MEETING_MODE', () => {
    mockMode = 'MEETING_MODE'

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(true)
    })

    expect(mockStopCapture).toHaveBeenCalledTimes(1)
  })

  it('should generate a unique speaker turn ID containing meeting ID', () => {
    mockMode = 'MEETING_MODE'

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(false)
    })

    const turnId = mockStartCapture.mock.calls[0][0] as string
    expect(turnId).toMatch(/^42-\d+$/)
  })
})

describe('useMeetingAudioCapture — FREE_MODE behavior', () => {
  it('should stop any stray capture when mic is unmuted in FREE_MODE', () => {
    mockMode = 'FREE_MODE'

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(false)
    })

    expect(mockStopCapture).toHaveBeenCalledTimes(1)
    expect(mockStartCapture).not.toHaveBeenCalled()
  })

  it('should not start capture when mic is muted in FREE_MODE', () => {
    mockMode = 'FREE_MODE'

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(true)
    })

    expect(mockStartCapture).not.toHaveBeenCalled()
    expect(mockStopCapture).not.toHaveBeenCalled()
  })
})

describe('useMeetingAudioCapture — host/secretary auto-revoke', () => {
  it('should revoke speaking permission when host unmutes and someone has permission', () => {
    mockMode = 'MEETING_MODE'
    mockSpeakingPermission = {
      userId: 99,
      userName: 'Member User',
      speakerTurnId: '42-prev',
      grantedAt: '2024-01-15T09:05:00Z',
    }

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: true,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(false)
    })

    expect(mockRevokeSpeakingPermission).toHaveBeenCalledWith(42)
  })

  it('should revoke speaking permission when secretary unmutes and someone has permission', () => {
    mockMode = 'MEETING_MODE'
    mockSpeakingPermission = {
      userId: 99,
      userName: 'Member User',
      speakerTurnId: '42-prev',
      grantedAt: '2024-01-15T09:05:00Z',
    }

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: true,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(false)
    })

    expect(mockRevokeSpeakingPermission).toHaveBeenCalledWith(42)
  })

  it('should NOT revoke speaking permission when host unmutes and no one has permission', () => {
    mockMode = 'MEETING_MODE'
    mockSpeakingPermission = null

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: true,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(false)
    })

    expect(mockRevokeSpeakingPermission).not.toHaveBeenCalled()
  })

  it('should NOT revoke speaking permission when a regular member unmutes', () => {
    mockMode = 'MEETING_MODE'
    mockSpeakingPermission = {
      userId: 99,
      userName: 'Member User',
      speakerTurnId: '42-prev',
      grantedAt: '2024-01-15T09:05:00Z',
    }

    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    act(() => {
      result.current.handleAudioMuteStatusChanged(false)
    })

    expect(mockRevokeSpeakingPermission).not.toHaveBeenCalled()
  })
})

describe('useMeetingAudioCapture — return values', () => {
  it('should return isCapturing from useAudioCapture', () => {
    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    expect(result.current.isCapturing).toBe(false)
  })

  it('should return isPermissionDenied from useAudioCapture', () => {
    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    expect(result.current.isPermissionDenied).toBe(false)
  })

  it('should return handleAudioMuteStatusChanged as a function', () => {
    const { result } = renderHook(() =>
      useMeetingAudioCapture({
        meeting: MOCK_MEETING,
        jitsiRef: mockJitsiRef as React.RefObject<never>,
        isHost: false,
        isSecretary: false,
      }),
    )

    expect(typeof result.current.handleAudioMuteStatusChanged).toBe('function')
  })
})
