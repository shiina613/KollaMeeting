/**
 * Unit tests for MeetingRoom — JaaS token fetching scenarios.
 *
 * Tests:
 * 1. When JaaS enabled: shows loading indicator while fetching token
 * 2. When token fetch succeeds: JitsiFrame receives correct jwt and meetingCode (roomName format)
 * 3. When token fetch fails: shows error banner with retry button
 * 4. When JaaS disabled: JitsiFrame renders with meeting.meetingCode and no jwt
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.6
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, waitFor, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import React from 'react'
import type { Meeting } from '../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

// Mock jaasService
vi.mock('../../services/jaasService', () => ({
  fetchJaasToken: vi.fn(),
}))

// Mock meetingService to avoid side effects
vi.mock('../../services/meetingService', () => ({
  joinMeeting: vi.fn().mockResolvedValue({}),
  leaveMeeting: vi.fn().mockResolvedValue({}),
}))

// Mock useWebSocket to avoid STOMP connection
vi.mock('../../hooks/useWebSocket', () => ({
  default: vi.fn().mockReturnValue({ isConnected: false, disconnect: vi.fn(), reconnect: vi.fn() }),
}))

// Mock useTranscription
vi.mock('../../hooks/useTranscription', () => ({
  default: vi.fn().mockReturnValue({
    segments: [],
    isTranscriptionAvailable: true,
    addSegment: vi.fn(),
    handleTranscriptionEvent: vi.fn(),
    clearSegments: vi.fn(),
    setTranscriptionAvailable: vi.fn(),
  }),
}))

// Mock useAudioCapture
vi.mock('../../hooks/useAudioCapture', () => ({
  default: vi.fn().mockReturnValue({
    status: 'idle',
    startCapture: vi.fn(),
    stopCapture: vi.fn(),
    isCapturing: false,
    isPermissionDenied: false,
  }),
}))

// Mock useAuthStore
vi.mock('../../store/authStore', () => {
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

// Mock useMeetingStore
vi.mock('../../store/meetingStore', () => {
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

// Mock react-router-dom
vi.mock('react-router-dom', () => ({
  useNavigate: vi.fn().mockReturnValue(vi.fn()),
}))

// Mock JitsiFrame to capture props
vi.mock('./JitsiFrame', () => ({
  default: vi.fn(({ meetingCode, jwt }: { meetingCode: string; jwt?: string }) => (
    <div
      data-testid="jitsi-frame-mock"
      data-meeting-code={meetingCode}
      data-jwt={jwt ?? ''}
    />
  )),
}))

// Mock child components that have complex dependencies
vi.mock('./TranscriptionPanel', () => ({
  default: () => <div data-testid="transcription-panel-mock" />,
}))
vi.mock('./TranscriptionPriorityControl', () => ({
  default: () => <div data-testid="transcription-priority-control-mock" />,
}))
vi.mock('./RaiseHandPanel', () => ({
  default: () => <div data-testid="raise-hand-panel-mock" />,
}))
vi.mock('./RaiseHandButton', () => ({
  default: () => <div data-testid="raise-hand-button-mock" />,
}))
vi.mock('./MeetingModeToggle', () => ({
  default: () => <div data-testid="meeting-mode-toggle-mock" />,
}))
vi.mock('./ParticipantList', () => ({
  default: () => <div data-testid="participant-list-mock" />,
}))
vi.mock('./SpeakingPermissionBadge', () => ({
  default: () => <div data-testid="speaking-permission-badge-mock" />,
}))

// ─── Test data ────────────────────────────────────────────────────────────────

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
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function renderMeetingRoom() {
  const { default: MeetingRoom } = await import('./MeetingRoom')
  return render(<MeetingRoom meeting={mockMeeting} />)
}

// ─── Cleanup ──────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.resetModules()
})

afterEach(() => {
  vi.unstubAllEnvs()
  vi.clearAllMocks()
})

// ─── Test Suite 1: JaaS enabled — loading state ───────────────────────────────

describe('MeetingRoom — JaaS enabled: loading state', () => {
  it('shows loading indicator while fetching JaaS token', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')

    // Never resolves — keeps loading state
    const { fetchJaasToken } = await import('../../services/jaasService')
    vi.mocked(fetchJaasToken).mockReturnValue(new Promise(() => {}))

    await renderMeetingRoom()

    expect(screen.getByTestId('jaas-loading')).toBeInTheDocument()
    expect(screen.getByText('Đang kết nối JaaS...')).toBeInTheDocument()
    // JitsiFrame should NOT be rendered while loading
    expect(screen.queryByTestId('jitsi-frame-mock')).not.toBeInTheDocument()
  })
})

// ─── Test Suite 2: JaaS enabled — token fetch success ────────────────────────

describe('MeetingRoom — JaaS enabled: token fetch success', () => {
  it('passes jwt and roomName to JitsiFrame after successful token fetch', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')

    const { fetchJaasToken } = await import('../../services/jaasService')
    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: 'eyJhbGciOiJSUzI1NiJ9.test.signature',
      roomName: 'vpaas-magic-cookie-abc123/TESTCODE1234567890AB',
    })

    await renderMeetingRoom()

    await waitFor(() => {
      expect(screen.getByTestId('jitsi-frame-mock')).toBeInTheDocument()
    })

    const jitsiFrame = screen.getByTestId('jitsi-frame-mock')
    expect(jitsiFrame).toHaveAttribute(
      'data-meeting-code',
      'vpaas-magic-cookie-abc123/TESTCODE1234567890AB',
    )
    expect(jitsiFrame).toHaveAttribute('data-jwt', 'eyJhbGciOiJSUzI1NiJ9.test.signature')
  })

  it('does not show loading indicator after token is fetched', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')

    const { fetchJaasToken } = await import('../../services/jaasService')
    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: 'eyJhbGciOiJSUzI1NiJ9.test.signature',
      roomName: 'vpaas-magic-cookie-abc123/TESTCODE1234567890AB',
    })

    await renderMeetingRoom()

    await waitFor(() => {
      expect(screen.queryByTestId('jaas-loading')).not.toBeInTheDocument()
    })
  })

  it('calls fetchJaasToken with the correct meeting id', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')

    const { fetchJaasToken } = await import('../../services/jaasService')
    vi.mocked(fetchJaasToken).mockResolvedValue({
      token: 'eyJhbGciOiJSUzI1NiJ9.test.signature',
      roomName: 'vpaas-magic-cookie-abc123/TESTCODE1234567890AB',
    })

    await renderMeetingRoom()

    await waitFor(() => {
      expect(vi.mocked(fetchJaasToken)).toHaveBeenCalledWith(42)
    })
  })
})

// ─── Test Suite 3: JaaS enabled — token fetch failure ────────────────────────

describe('MeetingRoom — JaaS enabled: token fetch failure', () => {
  it('shows error banner when token fetch fails', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')

    const { fetchJaasToken } = await import('../../services/jaasService')
    vi.mocked(fetchJaasToken).mockRejectedValue(new Error('Network error'))

    await renderMeetingRoom()

    await waitFor(() => {
      expect(screen.getByTestId('jaas-error')).toBeInTheDocument()
    })

    expect(screen.getByText('Không thể lấy token JaaS. Vui lòng thử lại.')).toBeInTheDocument()
    // JitsiFrame should NOT be rendered when there's an error
    expect(screen.queryByTestId('jitsi-frame-mock')).not.toBeInTheDocument()
  })

  it('shows retry button when token fetch fails', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')

    const { fetchJaasToken } = await import('../../services/jaasService')
    vi.mocked(fetchJaasToken).mockRejectedValue(new Error('Network error'))

    await renderMeetingRoom()

    await waitFor(() => {
      expect(screen.getByTestId('jaas-retry-button')).toBeInTheDocument()
    })

    expect(screen.getByTestId('jaas-retry-button')).toHaveTextContent('Thử lại')
  })

  it('retries token fetch when retry button is clicked', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')

    const { fetchJaasToken } = await import('../../services/jaasService')
    // First call fails, second call succeeds
    vi.mocked(fetchJaasToken)
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce({
        token: 'eyJhbGciOiJSUzI1NiJ9.test.signature',
        roomName: 'vpaas-magic-cookie-abc123/TESTCODE1234567890AB',
      })

    await renderMeetingRoom()

    // Wait for error state
    await waitFor(() => {
      expect(screen.getByTestId('jaas-retry-button')).toBeInTheDocument()
    })

    // Click retry
    await act(async () => {
      await userEvent.click(screen.getByTestId('jaas-retry-button'))
    })

    // After retry succeeds, JitsiFrame should appear
    await waitFor(() => {
      expect(screen.getByTestId('jitsi-frame-mock')).toBeInTheDocument()
    })

    expect(vi.mocked(fetchJaasToken)).toHaveBeenCalledTimes(2)
  })
})

// ─── Test Suite 4: JaaS disabled ─────────────────────────────────────────────

describe('MeetingRoom — JaaS disabled', () => {
  it('renders JitsiFrame with meeting.meetingCode when JaaS is disabled', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')

    await renderMeetingRoom()

    await waitFor(() => {
      expect(screen.getByTestId('jitsi-frame-mock')).toBeInTheDocument()
    })

    const jitsiFrame = screen.getByTestId('jitsi-frame-mock')
    expect(jitsiFrame).toHaveAttribute('data-meeting-code', 'TESTCODE1234567890AB')
  })

  it('does not pass jwt to JitsiFrame when JaaS is disabled', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')

    await renderMeetingRoom()

    await waitFor(() => {
      expect(screen.getByTestId('jitsi-frame-mock')).toBeInTheDocument()
    })

    const jitsiFrame = screen.getByTestId('jitsi-frame-mock')
    // jwt should be empty string (undefined passed as data attribute)
    expect(jitsiFrame).toHaveAttribute('data-jwt', '')
  })

  it('does not call fetchJaasToken when JaaS is disabled', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')

    const { fetchJaasToken } = await import('../../services/jaasService')

    await renderMeetingRoom()

    await waitFor(() => {
      expect(screen.getByTestId('jitsi-frame-mock')).toBeInTheDocument()
    })

    expect(vi.mocked(fetchJaasToken)).not.toHaveBeenCalled()
  })

  it('does not show loading indicator when JaaS is disabled', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')

    await renderMeetingRoom()

    expect(screen.queryByTestId('jaas-loading')).not.toBeInTheDocument()
  })
})
