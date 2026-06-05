/**
 * Unit tests for MeetingRoom — Dismissible error banners.
 *
 * Tests:
 * 1. Error banner displays with dismiss button (Req 12.1)
 * 2. Auto-dismiss after 10 seconds with fade-out (Req 12.2)
 * 3. Dismiss button immediately hides banner with fade-out (Req 12.3)
 * 4. Uses transition-opacity duration-300 for fade-out animation
 * 5. Shows amber styling for join errors
 *
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import React from 'react'
import type { Meeting } from '../../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../../../services/jaasService', () => ({
  fetchJaasToken: vi.fn().mockResolvedValue({ token: 'test-token', roomName: 'test-room' }),
}))

const joinMeetingMock = vi.fn().mockResolvedValue({})
const leaveMeetingMock = vi.fn().mockResolvedValue({})

vi.mock('../../../services/meetingService', () => ({
  joinMeeting: (...args: unknown[]) => joinMeetingMock(...args),
  leaveMeeting: (...args: unknown[]) => leaveMeetingMock(...args),
}))

vi.mock('../../../hooks/useWebSocket', () => ({
  default: vi.fn().mockReturnValue({ isConnected: false, disconnect: vi.fn(), reconnect: vi.fn() }),
}))

vi.mock('../../../hooks/useTranscription', () => ({
  default: () => ({
    segments: [],
    isTranscriptionAvailable: true,
    addSegment: vi.fn(),
    handleTranscriptionEvent: vi.fn(),
    clearSegments: vi.fn(),
    setTranscriptionAvailable: vi.fn(),
  }),
}))

vi.mock('../../../hooks/useAudioCapture', () => ({
  default: vi.fn().mockReturnValue({
    status: 'idle',
    startCapture: vi.fn(),
    stopCapture: vi.fn(),
    isCapturing: false,
    isPermissionDenied: false,
  }),
}))

vi.mock('../../../store/authStore', () => {
  const mockUser = {
    id: 1,
    username: 'testuser',
    fullName: 'Test User',
    email: 'test@example.com',
    role: 'USER' as const,
    isActive: true,
  }
  const useAuthStore = vi.fn(() => ({ user: mockUser, token: 'test-token' }))
  useAuthStore.getState = vi.fn(() => ({ user: mockUser, token: 'test-token' }))
  return { default: useAuthStore }
})

vi.mock('../../../store/meetingStore', () => {
  const useMeetingStore = vi.fn(() => ({
    setActiveMeeting: vi.fn(),
    clearActiveMeeting: vi.fn(),
    handleMeetingEvent: vi.fn(),
    mode: 'FREE_MODE',
    speakingPermission: null,
    raiseHandRequests: [],
    participants: [],
    isTranscriptionAvailable: true,
  }))
  useMeetingStore.getState = vi.fn(() => ({
    speakingPermission: null,
    setActiveMeeting: vi.fn(),
    clearActiveMeeting: vi.fn(),
    handleMeetingEvent: vi.fn(),
    mode: 'FREE_MODE',
  }))
  return { default: useMeetingStore }
})

vi.mock('../../../store/toastStore', () => {
  const useToastStore = vi.fn(() => ({ toasts: [] }))
  useToastStore.getState = vi.fn(() => ({ toasts: [], addToast: vi.fn(), removeToast: vi.fn(), clearAll: vi.fn() }))
  return { default: useToastStore, createToastMessage: vi.fn().mockReturnValue(null) }
})

vi.mock('react-router-dom', () => ({
  useNavigate: vi.fn().mockReturnValue(vi.fn()),
}))

// Mock child components to avoid complex dependencies
vi.mock('../JitsiFrame', () => ({
  default: vi.fn(() => <div data-testid="jitsi-frame-mock" />),
}))
vi.mock('../TopBar', () => ({
  default: vi.fn(() => <div data-testid="top-bar-mock" />),
}))
vi.mock('../BottomBar', () => ({
  default: vi.fn(() => <div data-testid="bottom-bar-mock" />),
}))
vi.mock('../Sidebar', () => ({
  default: vi.fn(() => <div data-testid="sidebar-mock" />),
}))
vi.mock('../ToastContainer', () => ({
  default: vi.fn(() => <div data-testid="toast-container-mock" />),
}))
vi.mock('../ShortcutsHelpOverlay', () => ({
  default: vi.fn(() => <div data-testid="shortcuts-help-mock" />),
}))

// ─── Test Data ────────────────────────────────────────────────────────────────

const mockMeeting: Meeting = {
  id: 42,
  title: 'Test Meeting',
  meetingCode: 'TESTCODE1234567890AB',
  status: 'ACTIVE',
  mode: 'FREE_MODE',
  transcriptionPriority: 'NORMAL_PRIORITY',
  startTime: '2025-01-01T09:00:00+07:00',
  endTime: '2025-01-01T10:00:00+07:00',
  room: { id: 1, name: 'Room A', department: { id: 1, name: 'Dept A' } },
  hostUser: {
    id: 2,
    username: 'host',
    fullName: 'Host User',
    email: 'host@example.com',
    role: 'ADMIN',
    isActive: true,
  },
  secretaryUser: {
    id: 3,
    username: 'secretary',
    fullName: 'Secretary User',
    email: 'secretary@example.com',
    role: 'SECRETARY',
    isActive: true,
  },
  createdBy: {
    id: 2,
    username: 'host',
    fullName: 'Host User',
    email: 'host@example.com',
    role: 'ADMIN',
    isActive: true,
  },
} as unknown as Meeting

// ─── Import component (after mocks) ──────────────────────────────────────────

import MeetingRoom from '../MeetingRoom'

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('MeetingRoom — Dismissible error banners', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.stubEnv('VITE_JAAS_APP_ID', '')
    joinMeetingMock.mockRejectedValue(new Error('Network error'))
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllEnvs()
    vi.clearAllMocks()
    joinMeetingMock.mockResolvedValue({})
  })

  async function renderAndWaitForError() {
    await act(async () => {
      render(<MeetingRoom meeting={mockMeeting} />)
    })
    // Flush microtasks so the rejected promise in useMeetingLifecycle triggers onJoinError
    await act(async () => {
      await Promise.resolve()
    })
  }

  it('displays join error banner with dismiss button (Req 12.1)', async () => {
    await renderAndWaitForError()

    expect(screen.getByTestId('error-banner')).toBeInTheDocument()
    expect(screen.getByTestId('error-banner-dismiss')).toBeInTheDocument()
    expect(screen.getByLabelText('Dismiss error')).toBeInTheDocument()
  })

  it('auto-dismisses error banner after 10 seconds with fade-out (Req 12.2)', async () => {
    await renderAndWaitForError()

    // Banner should be fully visible initially
    const banner = screen.getByTestId('error-banner')
    expect(banner.className).toContain('opacity-100')

    // Advance 10 seconds — fade-out starts
    act(() => {
      vi.advanceTimersByTime(10000)
    })

    expect(banner.className).toContain('opacity-0')

    // Advance 300ms — banner removed after fade-out animation
    act(() => {
      vi.advanceTimersByTime(300)
    })

    expect(screen.queryByTestId('error-banner')).not.toBeInTheDocument()
  })

  it('dismiss button immediately hides banner with fade-out (Req 12.3)', async () => {
    await renderAndWaitForError()

    // Click dismiss button
    const dismissBtn = screen.getByTestId('error-banner-dismiss')
    act(() => {
      dismissBtn.click()
    })

    // Banner should start fading
    const banner = screen.getByTestId('error-banner')
    expect(banner.className).toContain('opacity-0')

    // After 300ms fade-out, banner is removed
    act(() => {
      vi.advanceTimersByTime(300)
    })

    expect(screen.queryByTestId('error-banner')).not.toBeInTheDocument()
  })

  it('uses transition-opacity duration-300 for fade-out animation', async () => {
    await renderAndWaitForError()

    const banner = screen.getByTestId('error-banner')
    expect(banner.className).toContain('transition-opacity')
    expect(banner.className).toContain('duration-300')
  })

  it('shows amber styling for join errors', async () => {
    await renderAndWaitForError()

    const banner = screen.getByTestId('error-banner')
    expect(banner.className).toContain('bg-amber-50')
    expect(banner.className).toContain('text-amber-800')
  })
})
