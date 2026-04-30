/**
 * Component tests for RaiseHandPanel.
 *
 * Tests:
 * 1. Renders empty state when no requests
 * 2. Renders requests in chronological order (oldest first)
 * 3. Shows Grant button for pending requests
 * 4. Shows Revoke button for the current speaker
 * 5. Calls grantSpeakingPermission API on Grant click
 * 6. Calls revokeSpeakingPermission API on Revoke click
 * 7. Shows error message on API failure
 * 8. Renders nothing when isHost is false
 * 9. Shows synthetic speaker entry when speaker is not in raise-hand list
 * 10. Disables Grant button while granting
 *
 * Requirements: 20.4, 22.3, 22.9
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import RaiseHandPanel from './RaiseHandPanel'
import useMeetingStore from '../../store/meetingStore'
import type { RaiseHandRequest, SpeakingPermission } from '../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../../services/meetingService', () => ({
  listRaiseHandRequests: vi.fn().mockResolvedValue({ data: [], success: true }),
  grantSpeakingPermission: vi.fn(),
  revokeSpeakingPermission: vi.fn(),
}))

import {
  listRaiseHandRequests,
  grantSpeakingPermission,
  revokeSpeakingPermission,
} from '../../services/meetingService'

// ─── Test data ────────────────────────────────────────────────────────────────

const makeRequest = (
  userId: number,
  userName: string,
  requestedAt: string,
): RaiseHandRequest => ({ userId, userName, requestedAt })

const makeSpeaker = (userId: number, userName: string): SpeakingPermission => ({
  userId,
  userName,
  speakerTurnId: `turn-${userId}`,
  grantedAt: '2025-01-01T10:00:00+07:00',
})

// Requests in non-chronological order to test sorting
const REQUEST_A = makeRequest(1, 'Nguyễn Văn A', '2025-01-01T10:00:00+07:00') // oldest
const REQUEST_B = makeRequest(2, 'Trần Thị B', '2025-01-01T10:01:00+07:00')
const REQUEST_C = makeRequest(3, 'Lê Văn C', '2025-01-01T10:02:00+07:00') // newest

// ─── Helpers ──────────────────────────────────────────────────────────────────

function renderPanel(props: { meetingId?: number; isHost?: boolean } = {}) {
  return render(
    <RaiseHandPanel
      meetingId={props.meetingId ?? 1}
      isHost={props.isHost ?? true}
    />,
  )
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(listRaiseHandRequests).mockResolvedValue({ data: [], success: true })
  // Reset store to clean state
  useMeetingStore.setState({
    raiseHandRequests: [],
    speakingPermission: null,
    mode: 'MEETING_MODE',
  })
})

// ─── Test 1: Empty state ──────────────────────────────────────────────────────

describe('RaiseHandPanel — empty state', () => {
  it('shows empty state message when no requests', async () => {
    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('raise-hand-empty')).toBeInTheDocument()
    })
    expect(screen.getByText('Chưa có yêu cầu phát biểu')).toBeInTheDocument()
  })

  it('does not show the list when empty', async () => {
    renderPanel()

    await waitFor(() => {
      expect(screen.queryByTestId('raise-hand-list')).not.toBeInTheDocument()
    })
  })
})

// ─── Test 2: Chronological order ─────────────────────────────────────────────

describe('RaiseHandPanel — chronological order rendering', () => {
  it('renders requests in chronological order (oldest first)', async () => {
    // Seed store with requests in reverse order
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_C, REQUEST_A, REQUEST_B], // out of order
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('raise-hand-list')).toBeInTheDocument()
    })

    const items = screen.getAllByTestId(/^raise-hand-item-/)
    expect(items).toHaveLength(3)

    // First item should be the oldest (REQUEST_A — userId 1)
    expect(items[0]).toHaveAttribute('data-testid', 'raise-hand-item-1')
    // Second item should be REQUEST_B — userId 2
    expect(items[1]).toHaveAttribute('data-testid', 'raise-hand-item-2')
    // Third item should be the newest (REQUEST_C — userId 3)
    expect(items[2]).toHaveAttribute('data-testid', 'raise-hand-item-3')
  })

  it('shows all participant names', async () => {
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A, REQUEST_B, REQUEST_C],
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByText('Nguyễn Văn A')).toBeInTheDocument()
    })
    expect(screen.getByText('Trần Thị B')).toBeInTheDocument()
    expect(screen.getByText('Lê Văn C')).toBeInTheDocument()
  })

  it('shows count badge with correct number', async () => {
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A, REQUEST_B],
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByLabelText('2 yêu cầu')).toBeInTheDocument()
    })
  })
})

// ─── Test 3: Grant button ─────────────────────────────────────────────────────

describe('RaiseHandPanel — Grant button', () => {
  it('shows Grant button for each pending request', async () => {
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A, REQUEST_B],
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('grant-btn-1')).toBeInTheDocument()
    })
    expect(screen.getByTestId('grant-btn-2')).toBeInTheDocument()
  })

  it('does NOT show Grant button for the current speaker', async () => {
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A],
      speakingPermission: makeSpeaker(1, 'Nguyễn Văn A'),
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.queryByTestId('grant-btn-1')).not.toBeInTheDocument()
    })
  })
})

// ─── Test 4: Revoke button ────────────────────────────────────────────────────

describe('RaiseHandPanel — Revoke button', () => {
  it('shows Revoke button for the current speaker', async () => {
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A],
      speakingPermission: makeSpeaker(1, 'Nguyễn Văn A'),
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('revoke-btn-1')).toBeInTheDocument()
    })
  })

  it('does NOT show Revoke button for non-speakers', async () => {
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A, REQUEST_B],
      speakingPermission: makeSpeaker(1, 'Nguyễn Văn A'),
    })

    renderPanel()

    await waitFor(() => {
      // User 2 is not the speaker — should have Grant, not Revoke
      expect(screen.queryByTestId('revoke-btn-2')).not.toBeInTheDocument()
      expect(screen.getByTestId('grant-btn-2')).toBeInTheDocument()
    })
  })
})

// ─── Test 5: Grant API call ───────────────────────────────────────────────────

describe('RaiseHandPanel — Grant API call', () => {
  it('calls grantSpeakingPermission with correct meetingId and userId', async () => {
    vi.mocked(grantSpeakingPermission).mockResolvedValue({ data: undefined, success: true })
    useMeetingStore.setState({ raiseHandRequests: [REQUEST_A] })

    renderPanel({ meetingId: 42 })

    await waitFor(() => {
      expect(screen.getByTestId('grant-btn-1')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId('grant-btn-1'))

    await waitFor(() => {
      expect(vi.mocked(grantSpeakingPermission)).toHaveBeenCalledWith(42, 1)
    })
  })

  it('disables Grant button while granting', async () => {
    // Never resolves — keeps button in loading state
    vi.mocked(grantSpeakingPermission).mockReturnValue(new Promise(() => {}))
    useMeetingStore.setState({ raiseHandRequests: [REQUEST_A] })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('grant-btn-1')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId('grant-btn-1'))

    expect(screen.getByTestId('grant-btn-1')).toBeDisabled()
  })
})

// ─── Test 6: Revoke API call ──────────────────────────────────────────────────

describe('RaiseHandPanel — Revoke API call', () => {
  it('calls revokeSpeakingPermission with correct meetingId', async () => {
    vi.mocked(revokeSpeakingPermission).mockResolvedValue({ data: undefined, success: true })
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A],
      speakingPermission: makeSpeaker(1, 'Nguyễn Văn A'),
    })

    renderPanel({ meetingId: 42 })

    await waitFor(() => {
      expect(screen.getByTestId('revoke-btn-1')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId('revoke-btn-1'))

    await waitFor(() => {
      expect(vi.mocked(revokeSpeakingPermission)).toHaveBeenCalledWith(42)
    })
  })
})

// ─── Test 7: Error handling ───────────────────────────────────────────────────

describe('RaiseHandPanel — error handling', () => {
  it('shows error message when grantSpeakingPermission fails', async () => {
    vi.mocked(grantSpeakingPermission).mockRejectedValue({
      response: { data: { message: 'Không có quyền cấp phép' } },
    })
    useMeetingStore.setState({ raiseHandRequests: [REQUEST_A] })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('grant-btn-1')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId('grant-btn-1'))

    await waitFor(() => {
      expect(screen.getByTestId('raise-hand-error')).toHaveTextContent(
        'Không có quyền cấp phép',
      )
    })
  })

  it('shows fallback error message when API error has no message', async () => {
    vi.mocked(grantSpeakingPermission).mockRejectedValue(new Error('Network error'))
    useMeetingStore.setState({ raiseHandRequests: [REQUEST_A] })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('grant-btn-1')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId('grant-btn-1'))

    await waitFor(() => {
      expect(screen.getByTestId('raise-hand-error')).toHaveTextContent(
        'Không thể cấp quyền phát biểu. Vui lòng thử lại.',
      )
    })
  })

  it('shows error message when revokeSpeakingPermission fails', async () => {
    vi.mocked(revokeSpeakingPermission).mockRejectedValue(new Error('fail'))
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A],
      speakingPermission: makeSpeaker(1, 'Nguyễn Văn A'),
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('revoke-btn-1')).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId('revoke-btn-1'))

    await waitFor(() => {
      expect(screen.getByTestId('raise-hand-error')).toHaveTextContent(
        'Không thể thu hồi quyền phát biểu. Vui lòng thử lại.',
      )
    })
  })
})

// ─── Test 8: Non-host renders nothing ────────────────────────────────────────

describe('RaiseHandPanel — non-host', () => {
  it('renders nothing when isHost is false', () => {
    useMeetingStore.setState({ raiseHandRequests: [REQUEST_A] })

    const { container } = renderPanel({ isHost: false })

    expect(container).toBeEmptyDOMElement()
  })
})

// ─── Test 9: Synthetic speaker entry ─────────────────────────────────────────

describe('RaiseHandPanel — synthetic speaker entry', () => {
  it('shows Revoke button for speaker who is not in raise-hand list', async () => {
    // Speaker was granted directly (not via raise-hand), so not in raiseHandRequests
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_B], // only user 2 raised hand
      speakingPermission: makeSpeaker(99, 'Người dùng 99'), // user 99 is speaking
    })

    renderPanel()

    await waitFor(() => {
      // Synthetic entry for user 99 should appear with Revoke button
      expect(screen.getByTestId('revoke-btn-99')).toBeInTheDocument()
    })
    // User 2 should still have Grant button
    expect(screen.getByTestId('grant-btn-2')).toBeInTheDocument()
  })
})

// ─── Test 10: Initial fetch ───────────────────────────────────────────────────

describe('RaiseHandPanel — initial fetch', () => {
  it('fetches raise-hand requests on mount', async () => {
    vi.mocked(listRaiseHandRequests).mockResolvedValue({
      data: [REQUEST_A, REQUEST_B],
      success: true,
    })

    renderPanel({ meetingId: 5 })

    await waitFor(() => {
      expect(vi.mocked(listRaiseHandRequests)).toHaveBeenCalledWith(5)
    })
  })

  it('does not fetch when isHost is false', () => {
    renderPanel({ isHost: false })

    expect(vi.mocked(listRaiseHandRequests)).not.toHaveBeenCalled()
  })
})

// ─── Test 11: Accessibility ───────────────────────────────────────────────────

describe('RaiseHandPanel — accessibility', () => {
  it('has correct aria-label on the panel', () => {
    renderPanel()

    expect(screen.getByTestId('raise-hand-panel')).toHaveAttribute(
      'aria-label',
      'Danh sách xin phát biểu',
    )
  })

  it('list has correct aria-label', async () => {
    useMeetingStore.setState({ raiseHandRequests: [REQUEST_A] })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('raise-hand-list')).toHaveAttribute(
        'aria-label',
        'Danh sách yêu cầu phát biểu theo thứ tự thời gian',
      )
    })
  })

  it('Grant button has descriptive aria-label', async () => {
    useMeetingStore.setState({ raiseHandRequests: [REQUEST_A] })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('grant-btn-1')).toHaveAttribute(
        'aria-label',
        'Cấp quyền phát biểu cho Nguyễn Văn A',
      )
    })
  })

  it('Revoke button has descriptive aria-label', async () => {
    useMeetingStore.setState({
      raiseHandRequests: [REQUEST_A],
      speakingPermission: makeSpeaker(1, 'Nguyễn Văn A'),
    })

    renderPanel()

    await waitFor(() => {
      expect(screen.getByTestId('revoke-btn-1')).toHaveAttribute(
        'aria-label',
        'Thu hồi quyền phát biểu của Nguyễn Văn A',
      )
    })
  })
})
