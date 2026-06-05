/**
 * Unit tests for TopBar component.
 *
 * Tests:
 * - Renders meeting title with truncation classes
 * - Renders meeting code (visible on md+)
 * - Renders MeetingModeToggle
 * - Renders SpeakingPermissionBadge
 * - Renders REC indicator when capturing
 * - Hides REC indicator when not capturing
 * - Renders sidebar toggle button
 * - Renders ConnectionQualityIndicator
 * - Responsive: two-row layout on < 640px (row-2 element present)
 * - Sidebar toggle calls onToggleSidebar
 *
 * Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 9.1, 9.2, 9.3, 9.4, 9.5
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import TopBar, { type TopBarProps } from '../TopBar'
import type { Meeting } from '../../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../MeetingModeToggle', () => ({
  default: (props: { meetingId: number }) => (
    <div data-testid="mock-meeting-mode-toggle" data-meeting-id={props.meetingId} />
  ),
}))

vi.mock('../SpeakingPermissionBadge', () => ({
  default: (props: { currentUserId?: number }) => (
    <div data-testid="mock-speaking-permission-badge" data-user-id={props.currentUserId} />
  ),
}))

vi.mock('../ConnectionQualityIndicator', () => ({
  default: (props: { stats: unknown }) => (
    <div data-testid="mock-connection-quality-indicator" data-stats={JSON.stringify(props.stats)} />
  ),
}))

vi.mock('../../../store/authStore', () => ({
  default: () => ({ user: { id: 1, fullName: 'Test User', username: 'testuser' } }),
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

const createMeeting = (overrides?: Partial<Meeting>): Meeting => ({
  id: 1,
  title: 'Test Meeting Title',
  meetingCode: 'MTG-001',
  status: 'ACTIVE',
  mode: 'FREE_MODE',
  transcriptionPriority: 'NORMAL_PRIORITY',
  startTime: '2024-01-01T10:00:00Z',
  endTime: '2024-01-01T11:00:00Z',
  room: { id: 1, name: 'Room A', capacity: 10, location: 'Floor 1' },
  hostUser: { id: 1, username: 'host', fullName: 'Host User', departmentName: 'IT' },
  secretaryUser: { id: 2, username: 'secretary', fullName: 'Secretary User', departmentName: 'IT' },
  createdBy: { id: 1, username: 'host', fullName: 'Host User', departmentName: 'IT' },
  ...overrides,
})

const defaultProps: TopBarProps = {
  meeting: createMeeting(),
  isHost: true,
  isSecretary: false,
  conferenceReady: true,
  isCapturing: false,
  isSidebarOpen: true,
  onToggleSidebar: vi.fn(),
  onModeChanged: vi.fn(),
  connectionStats: null,
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('TopBar', () => {
  it('renders the top bar container with data-testid', () => {
    render(<TopBar {...defaultProps} />)
    expect(screen.getByTestId('top-bar')).toBeInTheDocument()
  })

  it('renders meeting title with truncation classes', () => {
    render(<TopBar {...defaultProps} />)
    const title = screen.getByTestId('top-bar-title')
    expect(title).toHaveTextContent('Test Meeting Title')
    expect(title).toHaveClass('truncate')
    expect(title).toHaveClass('max-w-[120px]')
  })

  it('renders meeting code with hidden on mobile class', () => {
    render(<TopBar {...defaultProps} />)
    const code = screen.getByTestId('top-bar-meeting-code')
    expect(code).toHaveTextContent('MTG-001')
    expect(code).toHaveClass('hidden', 'md:inline')
  })

  it('renders MeetingModeToggle', () => {
    render(<TopBar {...defaultProps} />)
    expect(screen.getByTestId('mock-meeting-mode-toggle')).toBeInTheDocument()
  })

  it('renders SpeakingPermissionBadge', () => {
    render(<TopBar {...defaultProps} />)
    const badges = screen.getAllByTestId('mock-speaking-permission-badge')
    expect(badges.length).toBeGreaterThan(0)
  })

  it('renders REC indicator when isCapturing is true', () => {
    render(<TopBar {...defaultProps} isCapturing={true} />)
    expect(screen.getByTestId('top-bar-rec-indicator')).toBeInTheDocument()
  })

  it('does not render REC indicator when isCapturing is false', () => {
    render(<TopBar {...defaultProps} isCapturing={false} />)
    expect(screen.queryByTestId('top-bar-rec-indicator')).not.toBeInTheDocument()
  })

  it('renders sidebar toggle button', () => {
    render(<TopBar {...defaultProps} />)
    const toggle = screen.getByTestId('sidebar-toggle')
    expect(toggle).toBeInTheDocument()
    expect(toggle).toHaveAttribute('aria-label', 'Ẩn thanh bên')
  })

  it('renders sidebar toggle with open icon when sidebar is closed', () => {
    render(<TopBar {...defaultProps} isSidebarOpen={false} />)
    const toggle = screen.getByTestId('sidebar-toggle')
    expect(toggle).toHaveAttribute('aria-label', 'Hiện thanh bên')
  })

  it('calls onToggleSidebar when sidebar toggle is clicked', () => {
    const onToggleSidebar = vi.fn()
    render(<TopBar {...defaultProps} onToggleSidebar={onToggleSidebar} />)
    fireEvent.click(screen.getByTestId('sidebar-toggle'))
    expect(onToggleSidebar).toHaveBeenCalledTimes(1)
  })

  it('renders ConnectionQualityIndicator', () => {
    render(<TopBar {...defaultProps} connectionStats={{ latency: 50, packetLoss: 0 }} />)
    expect(screen.getByTestId('mock-connection-quality-indicator')).toBeInTheDocument()
  })

  it('renders second row container for mobile layout', () => {
    render(<TopBar {...defaultProps} />)
    const row2 = screen.getByTestId('top-bar-row-2')
    expect(row2).toBeInTheDocument()
    // Row 2 should have sm:hidden class (visible only on < 640px)
    expect(row2).toHaveClass('sm:hidden')
  })

  it('renders REC indicator in mobile row when capturing', () => {
    render(<TopBar {...defaultProps} isCapturing={true} />)
    expect(screen.getByTestId('top-bar-rec-indicator-mobile')).toBeInTheDocument()
  })

  it('has accessible aria-label on the container', () => {
    render(<TopBar {...defaultProps} />)
    const container = screen.getByTestId('top-bar')
    expect(container).toHaveAttribute('aria-label', 'Thanh điều khiển cuộc họp')
  })

  it('passes meeting title as title attribute for tooltip on hover', () => {
    const longTitle = 'This is a very long meeting title that should be truncated'
    render(<TopBar {...defaultProps} meeting={createMeeting({ title: longTitle })} />)
    const title = screen.getByTestId('top-bar-title')
    expect(title).toHaveAttribute('title', longTitle)
  })
})
