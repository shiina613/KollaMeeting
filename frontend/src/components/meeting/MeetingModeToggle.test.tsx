/**
 * Component tests for MeetingModeToggle.
 *
 * Tests:
 * - Renders current mode badge correctly (FREE_MODE / MEETING_MODE)
 * - Shows switch only for moderator when conferenceReady
 * - Calls API with correct mode on toggle
 * - Shows loading state while switching
 * - Shows error message on API failure
 *
 * Requirements: 20.4
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MeetingModeToggle from './MeetingModeToggle'
import useMeetingStore from '../../store/meetingStore'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../../services/api', () => ({
  default: {
    post: vi.fn(),
  },
}))

vi.mock('../../store/authStore', () => {
  const mockUser = { id: 1, username: 'testuser', email: 'test@example.com', role: 'ADMIN' as const }
  const useAuthStore = vi.fn(() => ({ user: mockUser, token: 'test-token' }))
  useAuthStore.getState = vi.fn(() => ({ user: mockUser, token: 'test-token' }))
  return { default: useAuthStore }
})

import api from '../../services/api'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function renderToggle(props: {
  meetingId?: number
  canControlMode?: boolean
  conferenceReady?: boolean
  onModeChanged?: (mode: import('../../types/meeting').MeetingMode) => void
}) {
  return render(
    <MeetingModeToggle
      meetingId={props.meetingId ?? 1}
      canControlMode={props.canControlMode ?? false}
      conferenceReady={props.conferenceReady ?? true}
      onModeChanged={props.onModeChanged}
    />,
  )
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks()
  useMeetingStore.setState({ mode: 'FREE_MODE' })
})

// ─── Test 1: Mode badge display ───────────────────────────────────────────────

describe('MeetingModeToggle — mode badge display', () => {
  it('shows "Chế độ tự do" badge when mode is FREE_MODE', () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    renderToggle({ canControlMode: false, conferenceReady: false })

    expect(screen.getByTestId('mode-badge')).toHaveTextContent('Chế độ tự do')
  })

  it('shows "Chế độ họp" badge when mode is MEETING_MODE', () => {
    useMeetingStore.setState({ mode: 'MEETING_MODE' })
    renderToggle({ canControlMode: false, conferenceReady: false })

    expect(screen.getByTestId('mode-badge')).toHaveTextContent('Chế độ họp')
  })

  it('shows description text for FREE_MODE', () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    renderToggle({ canControlMode: false })

    expect(screen.getByText('Tất cả thành viên có thể bật mic đồng thời')).toBeInTheDocument()
  })

  it('shows description text for MEETING_MODE', () => {
    useMeetingStore.setState({ mode: 'MEETING_MODE' })
    renderToggle({ canControlMode: false })

    expect(screen.getByText('Chỉ người được cấp quyền mới có thể phát biểu')).toBeInTheDocument()
  })
})

// ─── Test 2: Mode switch visibility ──────────────────────────────────────────

describe('MeetingModeToggle — mode switch visibility', () => {
  it('shows switch when canControlMode and conferenceReady', () => {
    renderToggle({ canControlMode: true, conferenceReady: true })

    expect(screen.getByTestId('mode-toggle-button')).toBeInTheDocument()
  })

  it('does NOT show switch when canControlMode is false', () => {
    renderToggle({ canControlMode: false, conferenceReady: true })

    expect(screen.queryByTestId('mode-toggle-button')).not.toBeInTheDocument()
  })

  it('does NOT show switch when conferenceReady is false (still connecting)', () => {
    renderToggle({ canControlMode: true, conferenceReady: false })

    expect(screen.queryByTestId('mode-toggle-button')).not.toBeInTheDocument()
  })
})

// ─── Test 3: API call on toggle ───────────────────────────────────────────────

describe('MeetingModeToggle — API call on toggle', () => {
  it('calls POST /meetings/{id}/mode with MEETING_MODE when in FREE_MODE', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    vi.mocked(api.post).mockResolvedValue({ data: { success: true, data: {} } })

    renderToggle({ canControlMode: true, conferenceReady: true, meetingId: 42 })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(vi.mocked(api.post)).toHaveBeenCalledWith(
        '/meetings/42/mode',
        { mode: 'MEETING_MODE' },
      )
    })
  })

  it('calls POST /meetings/{id}/mode with FREE_MODE when in MEETING_MODE', async () => {
    useMeetingStore.setState({ mode: 'MEETING_MODE' })
    vi.mocked(api.post).mockResolvedValue({ data: { success: true, data: {} } })

    renderToggle({ canControlMode: true, conferenceReady: true, meetingId: 42 })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(vi.mocked(api.post)).toHaveBeenCalledWith(
        '/meetings/42/mode',
        { mode: 'FREE_MODE' },
      )
    })
  })

  it('calls onModeChanged callback with the new mode after successful API call', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    vi.mocked(api.post).mockResolvedValue({ data: { success: true, data: {} } })
    const onModeChanged = vi.fn()

    renderToggle({ canControlMode: true, conferenceReady: true, meetingId: 1, onModeChanged })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(onModeChanged).toHaveBeenCalledWith('MEETING_MODE')
    })
  })

  it('does NOT call onModeChanged when API fails', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    vi.mocked(api.post).mockRejectedValue(new Error('Network error'))
    const onModeChanged = vi.fn()

    renderToggle({ canControlMode: true, conferenceReady: true, meetingId: 1, onModeChanged })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(screen.getByTestId('mode-toggle-error')).toBeInTheDocument()
    })

    expect(onModeChanged).not.toHaveBeenCalled()
  })
})

// ─── Test 4: Loading state ────────────────────────────────────────────────────

describe('MeetingModeToggle — loading state', () => {
  it('disables the switch while switching', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })

    vi.mocked(api.post).mockReturnValue(new Promise(() => {}))

    renderToggle({ canControlMode: true, conferenceReady: true })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    expect(screen.getByTestId('mode-toggle-button')).toBeDisabled()
  })
})

// ─── Test 5: Error display ────────────────────────────────────────────────────

describe('MeetingModeToggle — error display', () => {
  it('shows error message when API returns an error with message', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    vi.mocked(api.post).mockRejectedValue({
      response: { data: { message: 'Bạn không có quyền chuyển chế độ' } },
    })

    renderToggle({ canControlMode: true, conferenceReady: true })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(screen.getByTestId('mode-toggle-error')).toHaveTextContent(
        'Bạn không có quyền chuyển chế độ',
      )
    })
  })

  it('shows fallback error message when API error has no message', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    vi.mocked(api.post).mockRejectedValue(new Error('Network error'))

    renderToggle({ canControlMode: true, conferenceReady: true })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(screen.getByTestId('mode-toggle-error')).toHaveTextContent(
        'Không thể chuyển chế độ họp. Vui lòng thử lại.',
      )
    })
  })

  it('clears error on next successful toggle', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })

    vi.mocked(api.post)
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce({ data: { success: true, data: {} } })

    renderToggle({ canControlMode: true, conferenceReady: true })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))
    await waitFor(() => {
      expect(screen.getByTestId('mode-toggle-error')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))
    await waitFor(() => {
      expect(screen.queryByTestId('mode-toggle-error')).not.toBeInTheDocument()
    })
  })
})

// ─── Test 6: Accessibility ────────────────────────────────────────────────────

describe('MeetingModeToggle — accessibility', () => {
  it('exposes role="switch" with aria-checked for FREE_MODE', () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    renderToggle({ canControlMode: true, conferenceReady: true })

    const sw = screen.getByTestId('mode-toggle-button')
    expect(sw).toHaveAttribute('role', 'switch')
    expect(sw).toHaveAttribute('aria-checked', 'false')
    expect(sw).toHaveAttribute(
      'aria-label',
      'Chuyển sang chế độ họp',
    )
  })

  it('exposes role="switch" with aria-checked for MEETING_MODE', () => {
    useMeetingStore.setState({ mode: 'MEETING_MODE' })
    renderToggle({ canControlMode: true, conferenceReady: true })

    const sw = screen.getByTestId('mode-toggle-button')
    expect(sw).toHaveAttribute('role', 'switch')
    expect(sw).toHaveAttribute('aria-checked', 'true')
    expect(sw).toHaveAttribute(
      'aria-label',
      'Chuyển sang chế độ tự do',
    )
  })

  it('mode badge has aria-live="polite"', () => {
    renderToggle({ canControlMode: false })

    expect(screen.getByTestId('mode-badge')).toHaveAttribute('aria-live', 'polite')
  })
})
