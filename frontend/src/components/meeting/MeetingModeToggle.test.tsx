/**
 * Component tests for MeetingModeToggle.
 *
 * Tests:
 * - Renders current mode badge correctly (FREE_MODE / MEETING_MODE)
 * - Shows toggle button only for Host
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
  // Zustand stores are callable hooks — mock as a function that returns state
  const useAuthStore = vi.fn(() => ({ user: mockUser, token: 'test-token' }))
  // Also expose getState for non-hook usage (e.g. api interceptor)
  useAuthStore.getState = vi.fn(() => ({ user: mockUser, token: 'test-token' }))
  return { default: useAuthStore }
})

import api from '../../services/api'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function renderToggle(props: {
  meetingId?: number
  isHost?: boolean
  onModeChanged?: (mode: import('../../types/meeting').MeetingMode) => void
}) {
  return render(
    <MeetingModeToggle
      meetingId={props.meetingId ?? 1}
      isHost={props.isHost ?? false}
      onModeChanged={props.onModeChanged}
    />,
  )
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks()
  // Reset meeting store to FREE_MODE
  useMeetingStore.setState({ mode: 'FREE_MODE' })
})

// ─── Test 1: Mode badge display ───────────────────────────────────────────────

describe('MeetingModeToggle — mode badge display', () => {
  it('shows "Chế độ tự do" badge when mode is FREE_MODE', () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    renderToggle({ isHost: false })

    expect(screen.getByTestId('mode-badge')).toHaveTextContent('Chế độ tự do')
  })

  it('shows "Chế độ họp" badge when mode is MEETING_MODE', () => {
    useMeetingStore.setState({ mode: 'MEETING_MODE' })
    renderToggle({ isHost: false })

    expect(screen.getByTestId('mode-badge')).toHaveTextContent('Chế độ họp')
  })

  it('shows description text for FREE_MODE', () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    renderToggle({ isHost: false })

    expect(screen.getByText('Tất cả thành viên có thể bật mic đồng thời')).toBeInTheDocument()
  })

  it('shows description text for MEETING_MODE', () => {
    useMeetingStore.setState({ mode: 'MEETING_MODE' })
    renderToggle({ isHost: false })

    expect(screen.getByText('Chỉ người được cấp quyền mới có thể phát biểu')).toBeInTheDocument()
  })
})

// ─── Test 2: Toggle button visibility ────────────────────────────────────────

describe('MeetingModeToggle — toggle button visibility', () => {
  it('shows toggle button when isHost is true', () => {
    renderToggle({ isHost: true })

    expect(screen.getByTestId('mode-toggle-button')).toBeInTheDocument()
  })

  it('does NOT show toggle button when isHost is false', () => {
    renderToggle({ isHost: false })

    expect(screen.queryByTestId('mode-toggle-button')).not.toBeInTheDocument()
  })
})

// ─── Test 3: API call on toggle ───────────────────────────────────────────────

describe('MeetingModeToggle — API call on toggle', () => {
  it('calls POST /meetings/{id}/mode with MEETING_MODE when in FREE_MODE', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    vi.mocked(api.post).mockResolvedValue({ data: { success: true, data: {} } })

    renderToggle({ isHost: true, meetingId: 42 })

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

    renderToggle({ isHost: true, meetingId: 42 })

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

    renderToggle({ isHost: true, meetingId: 1, onModeChanged })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(onModeChanged).toHaveBeenCalledWith('MEETING_MODE')
    })
  })

  it('does NOT call onModeChanged when API fails', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    vi.mocked(api.post).mockRejectedValue(new Error('Network error'))
    const onModeChanged = vi.fn()

    renderToggle({ isHost: true, meetingId: 1, onModeChanged })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(screen.getByTestId('mode-toggle-error')).toBeInTheDocument()
    })

    expect(onModeChanged).not.toHaveBeenCalled()
  })
})

// ─── Test 4: Loading state ────────────────────────────────────────────────────

describe('MeetingModeToggle — loading state', () => {
  it('disables the toggle button while switching', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })

    // Never resolves — keeps the button in loading state
    vi.mocked(api.post).mockReturnValue(new Promise(() => {}))

    renderToggle({ isHost: true })

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

    renderToggle({ isHost: true })

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

    renderToggle({ isHost: true })

    await userEvent.click(screen.getByTestId('mode-toggle-button'))

    await waitFor(() => {
      expect(screen.getByTestId('mode-toggle-error')).toHaveTextContent(
        'Không thể chuyển chế độ họp. Vui lòng thử lại.',
      )
    })
  })

  it('clears error on next successful toggle', async () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })

    // First call fails
    vi.mocked(api.post)
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce({ data: { success: true, data: {} } })

    renderToggle({ isHost: true })

    // First click — fails
    await userEvent.click(screen.getByTestId('mode-toggle-button'))
    await waitFor(() => {
      expect(screen.getByTestId('mode-toggle-error')).toBeInTheDocument()
    })

    // Second click — succeeds
    await userEvent.click(screen.getByTestId('mode-toggle-button'))
    await waitFor(() => {
      expect(screen.queryByTestId('mode-toggle-error')).not.toBeInTheDocument()
    })
  })
})

// ─── Test 6: Accessibility ────────────────────────────────────────────────────

describe('MeetingModeToggle — accessibility', () => {
  it('has correct aria-label on toggle button for FREE_MODE', () => {
    useMeetingStore.setState({ mode: 'FREE_MODE' })
    renderToggle({ isHost: true })

    expect(screen.getByTestId('mode-toggle-button')).toHaveAttribute(
      'aria-label',
      'Chuyển sang chế độ họp',
    )
  })

  it('has correct aria-label on toggle button for MEETING_MODE', () => {
    useMeetingStore.setState({ mode: 'MEETING_MODE' })
    renderToggle({ isHost: true })

    expect(screen.getByTestId('mode-toggle-button')).toHaveAttribute(
      'aria-label',
      'Chuyển sang chế độ tự do',
    )
  })

  it('mode badge has aria-live="polite"', () => {
    renderToggle({ isHost: false })

    expect(screen.getByTestId('mode-badge')).toHaveAttribute('aria-live', 'polite')
  })
})
