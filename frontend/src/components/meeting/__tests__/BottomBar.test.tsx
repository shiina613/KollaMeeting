/**
 * Unit tests for BottomBar component.
 *
 * Tests:
 * - Renders for all participant roles (host, secretary, member)
 * - Shows RaiseHandButton for non-host in MEETING_MODE
 * - Hides RaiseHandButton for host
 * - Hides RaiseHandButton in FREE_MODE
 * - Always displays MeetingTimer
 * - Uses dark background (slate-800)
 * - Compact mode classes for responsive layout
 *
 * Requirements: 3.1, 3.2, 3.3, 3.4, 3.5
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import BottomBar, { type BottomBarProps } from '../BottomBar'
import type { Meeting } from '../../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

const mockMode = vi.fn(() => 'MEETING_MODE')

vi.mock('../../../store/meetingStore', () => ({
  default: (selector: (s: { mode: string }) => unknown) =>
    selector({ mode: mockMode() }),
}))

vi.mock('../RaiseHandButton', () => ({
  default: (props: { meetingId: number; currentUserId: number }) => (
    <button data-testid="mock-raise-hand-button" data-meeting-id={props.meetingId} data-user-id={props.currentUserId}>
      Raise Hand
    </button>
  ),
}))

vi.mock('../MeetingTimer', () => ({
  default: (props: { joinedAt: string }) => (
    <div data-testid="mock-meeting-timer" data-joined-at={props.joinedAt}>
      00:00
    </div>
  ),
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

const createMeeting = (overrides?: Partial<Meeting>): Meeting => ({
  id: 1,
  title: 'Test Meeting',
  meetingCode: 'MTG-001',
  status: 'ACTIVE',
  mode: 'MEETING_MODE',
  transcriptionPriority: 'NORMAL_PRIORITY',
  startTime: '2024-01-01T10:00:00Z',
  endTime: '2024-01-01T11:00:00Z',
  room: { id: 1, name: 'Room A', capacity: 10 },
  hostUser: { id: 1, username: 'host', fullName: 'Host User', email: 'host@test.com', role: 'ADMIN', isActive: true },
  secretaryUser: { id: 2, username: 'secretary', fullName: 'Secretary User', email: 'sec@test.com', role: 'SECRETARY', isActive: true },
  createdBy: { id: 1, username: 'host', fullName: 'Host User', email: 'host@test.com', role: 'ADMIN', isActive: true },
  ...overrides,
})

const defaultProps: BottomBarProps = {
  meeting: createMeeting(),
  isHost: false,
  isSecretary: false,
  currentUserId: 3,
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('BottomBar', () => {
  beforeEach(() => {
    mockMode.mockReturnValue('MEETING_MODE')
  })

  it('renders the bottom bar container with data-testid', () => {
    render(<BottomBar {...defaultProps} />)
    expect(screen.getByTestId('bottom-bar')).toBeInTheDocument()
  })

  it('uses dark background (slate-800) matching video area', () => {
    render(<BottomBar {...defaultProps} />)
    const bar = screen.getByTestId('bottom-bar')
    expect(bar).toHaveClass('bg-slate-800')
  })

  it('renders for all participant roles — host', () => {
    render(<BottomBar {...defaultProps} isHost={true} />)
    expect(screen.getByTestId('bottom-bar')).toBeInTheDocument()
  })

  it('renders for all participant roles — secretary', () => {
    render(<BottomBar {...defaultProps} isSecretary={true} />)
    expect(screen.getByTestId('bottom-bar')).toBeInTheDocument()
  })

  it('renders for all participant roles — member', () => {
    render(<BottomBar {...defaultProps} isHost={false} isSecretary={false} />)
    expect(screen.getByTestId('bottom-bar')).toBeInTheDocument()
  })

  it('shows RaiseHandButton for non-host in MEETING_MODE', () => {
    render(<BottomBar {...defaultProps} isHost={false} currentUserId={3} />)
    expect(screen.getByTestId('mock-raise-hand-button')).toBeInTheDocument()
  })

  it('hides RaiseHandButton for host', () => {
    render(<BottomBar {...defaultProps} isHost={true} currentUserId={1} />)
    expect(screen.queryByTestId('mock-raise-hand-button')).not.toBeInTheDocument()
  })

  it('hides RaiseHandButton in FREE_MODE', () => {
    mockMode.mockReturnValue('FREE_MODE')
    render(<BottomBar {...defaultProps} isHost={false} currentUserId={3} />)
    expect(screen.queryByTestId('mock-raise-hand-button')).not.toBeInTheDocument()
  })

  it('hides RaiseHandButton when currentUserId is undefined', () => {
    render(<BottomBar {...defaultProps} isHost={false} currentUserId={undefined} />)
    expect(screen.queryByTestId('mock-raise-hand-button')).not.toBeInTheDocument()
  })

  it('always displays MeetingTimer', () => {
    render(<BottomBar {...defaultProps} />)
    expect(screen.getByTestId('mock-meeting-timer')).toBeInTheDocument()
  })

  it('passes meeting startTime to MeetingTimer as joinedAt', () => {
    render(<BottomBar {...defaultProps} />)
    const timer = screen.getByTestId('mock-meeting-timer')
    expect(timer).toHaveAttribute('data-joined-at', '2024-01-01T10:00:00Z')
  })

  it('has compact spacing classes for responsive layout (px-4 py-2 on mobile, md:px-6 md:py-3 on desktop)', () => {
    render(<BottomBar {...defaultProps} />)
    const bar = screen.getByTestId('bottom-bar')
    expect(bar).toHaveClass('px-4', 'py-2', 'md:px-6', 'md:py-3')
  })

  it('has accessible aria-label on the container', () => {
    render(<BottomBar {...defaultProps} />)
    const bar = screen.getByTestId('bottom-bar')
    expect(bar).toHaveAttribute('aria-label', 'Thanh hành động cuộc họp')
  })

  it('passes correct meetingId and currentUserId to RaiseHandButton', () => {
    render(<BottomBar {...defaultProps} meeting={createMeeting({ id: 42 })} currentUserId={7} />)
    const btn = screen.getByTestId('mock-raise-hand-button')
    expect(btn).toHaveAttribute('data-meeting-id', '42')
    expect(btn).toHaveAttribute('data-user-id', '7')
  })
})
