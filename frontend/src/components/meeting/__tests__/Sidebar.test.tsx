/**
 * Unit tests for Sidebar component.
 *
 * Tests:
 * - Renders nothing when isOpen is false
 * - Renders sidebar container when isOpen is true
 * - Renders dark theme (slate-800 background)
 * - Renders tab bar with role="tablist"
 * - Renders active tab with aria-selected="true"
 * - Renders inactive tabs with aria-selected="false"
 * - Renders tab panel with role="tabpanel" and aria-labelledby
 * - Renders close button in overlay mode (mobile)
 * - Calls onClose when close button is clicked
 * - Calls onClose when backdrop is clicked
 * - Calls onTabChange when a tab is clicked
 * - Renders resize handle (hidden on < lg via CSS class)
 * - Renders SkeletonLoader briefly on tab change
 * - Keyboard navigation: Arrow Right moves to next tab
 * - Keyboard navigation: Arrow Left moves to previous tab
 *
 * Requirements: 1.1, 1.2, 1.3, 6.1, 6.2, 6.3, 6.4, 6.5, 10.1, 10.2, 10.3, 14.1, 15.1
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import Sidebar, { type SidebarProps, type SidebarTab } from '../Sidebar'
import type { Meeting } from '../../../types/meeting'
import React from 'react'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../ParticipantList', () => ({
  default: () => <div data-testid="mock-participant-list" />,
}))

vi.mock('../RaiseHandPanel', () => ({
  default: (props: { meetingId: number; isHost: boolean }) => (
    <div data-testid="mock-raise-hand-panel" data-meeting-id={props.meetingId} />
  ),
}))

vi.mock('../TranscriptionPanel', () => ({
  default: (props: { meetingId: number }) => (
    <div data-testid="mock-transcription-panel" data-meeting-id={props.meetingId} />
  ),
}))

vi.mock('../SkeletonLoader', () => ({
  default: (props: { rows: number; variant: string }) => (
    <div data-testid="mock-skeleton-loader" data-rows={props.rows} data-variant={props.variant} />
  ),
}))

vi.mock('../../../store/meetingStore', () => ({
  default: (selector: (s: { mode: string }) => unknown) =>
    selector({ mode: 'MEETING_MODE' }),
}))

vi.mock('../../../hooks/useResizable', () => ({
  useResizable: () => ({
    width: 288,
    isDragging: false,
    isAtLimit: false,
    handleMouseDown: vi.fn(),
  }),
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

const createMeeting = (overrides?: Partial<Meeting>): Meeting => ({
  id: 1,
  title: 'Test Meeting',
  meetingCode: 'MTG-001',
  status: 'ACTIVE',
  mode: 'MEETING_MODE',
  transcriptionPriority: 'HIGH_PRIORITY',
  startTime: '2024-01-01T10:00:00Z',
  endTime: '2024-01-01T11:00:00Z',
  room: { id: 1, name: 'Room A', capacity: 10 },
  hostUser: { id: 1, username: 'host', fullName: 'Host User', email: 'host@test.com', role: 'ADMIN', isActive: true },
  secretaryUser: { id: 2, username: 'secretary', fullName: 'Secretary User', email: 'sec@test.com', role: 'SECRETARY', isActive: true },
  createdBy: { id: 1, username: 'host', fullName: 'Host User', email: 'host@test.com', role: 'ADMIN', isActive: true },
  ...overrides,
} as Meeting)

const createRef = <T,>(): React.RefObject<T> => ({ current: null }) as React.RefObject<T>

const defaultProps: SidebarProps = {
  segments: [],
  isTranscriptionAvailable: true,
  isOpen: true,
  onClose: vi.fn(),
  activeTab: 'participants' as SidebarTab,
  onTabChange: vi.fn(),
  meeting: createMeeting(),
  isHost: true,
  isSecretary: false,
  currentUserId: 1,
  sidebarRef: createRef<HTMLDivElement>(),
  toggleButtonRef: createRef<HTMLButtonElement>(),
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('Sidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders nothing when isOpen is false', () => {
    render(<Sidebar {...defaultProps} isOpen={false} />)
    expect(screen.queryByTestId('meeting-sidebar')).not.toBeInTheDocument()
  })

  it('renders sidebar container when isOpen is true', () => {
    render(<Sidebar {...defaultProps} />)
    expect(screen.getByTestId('meeting-sidebar')).toBeInTheDocument()
  })

  it('renders with dark theme (slate-800 background class)', () => {
    render(<Sidebar {...defaultProps} />)
    const sidebar = screen.getByTestId('meeting-sidebar')
    expect(sidebar).toHaveClass('bg-slate-800')
  })

  it('renders tab bar with role="tablist"', () => {
    render(<Sidebar {...defaultProps} />)
    const tablist = screen.getByTestId('sidebar-tablist')
    expect(tablist).toHaveAttribute('role', 'tablist')
    expect(tablist).toHaveAttribute('aria-label', 'Nội dung thanh bên')
  })

  it('renders active tab with aria-selected="true"', () => {
    render(<Sidebar {...defaultProps} activeTab="participants" />)
    const activeTab = screen.getByTestId('sidebar-tab-participants')
    expect(activeTab).toHaveAttribute('aria-selected', 'true')
    expect(activeTab).toHaveAttribute('role', 'tab')
  })

  it('renders inactive tabs with aria-selected="false"', () => {
    render(<Sidebar {...defaultProps} activeTab="participants" />)
    // raise-hand and transcription should be inactive
    const raiseHandTab = screen.getByTestId('sidebar-tab-raise-hand')
    expect(raiseHandTab).toHaveAttribute('aria-selected', 'false')
  })

  it('renders tab panel with role="tabpanel" and aria-labelledby', () => {
    render(<Sidebar {...defaultProps} activeTab="participants" />)
    const panel = screen.getByTestId('sidebar-panel-participants')
    expect(panel).toHaveAttribute('role', 'tabpanel')
    expect(panel).toHaveAttribute('aria-labelledby', 'sidebar-tab-participants')
  })

  it('renders close button for mobile overlay', () => {
    render(<Sidebar {...defaultProps} />)
    const closeBtn = screen.getByTestId('sidebar-close-button')
    expect(closeBtn).toBeInTheDocument()
    expect(closeBtn).toHaveAttribute('aria-label', 'Đóng thanh bên')
  })

  it('calls onClose when close button is clicked', () => {
    const onClose = vi.fn()
    render(<Sidebar {...defaultProps} onClose={onClose} />)
    fireEvent.click(screen.getByTestId('sidebar-close-button'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('calls onClose when backdrop is clicked', () => {
    const onClose = vi.fn()
    render(<Sidebar {...defaultProps} onClose={onClose} />)
    fireEvent.click(screen.getByTestId('sidebar-backdrop'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('calls onTabChange when a tab is clicked', () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...defaultProps} onTabChange={onTabChange} activeTab="participants" />)
    fireEvent.click(screen.getByTestId('sidebar-tab-raise-hand'))
    expect(onTabChange).toHaveBeenCalledWith('raise-hand')
  })

  it('renders resize handle with hidden on < lg class', () => {
    render(<Sidebar {...defaultProps} />)
    const handle = screen.getByTestId('sidebar-resize-handle')
    expect(handle).toBeInTheDocument()
    expect(handle).toHaveClass('hidden', 'lg:block')
  })

  it('renders ParticipantList when participants tab is active', () => {
    render(<Sidebar {...defaultProps} activeTab="participants" />)
    expect(screen.getByTestId('mock-participant-list')).toBeInTheDocument()
  })

  it('renders RaiseHandPanel when raise-hand tab is active', () => {
    render(<Sidebar {...defaultProps} activeTab="raise-hand" />)
    expect(screen.getByTestId('mock-raise-hand-panel')).toBeInTheDocument()
  })

  it('renders TranscriptionPanel when transcription tab is active', () => {
    render(<Sidebar {...defaultProps} activeTab="transcription" />)
    expect(screen.getByTestId('mock-transcription-panel')).toBeInTheDocument()
  })

  it('renders SkeletonLoader briefly on tab change', async () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...defaultProps} onTabChange={onTabChange} activeTab="participants" />)

    // Click a different tab to trigger loading state
    fireEvent.click(screen.getByTestId('sidebar-tab-raise-hand'))

    // Skeleton should appear briefly
    expect(screen.getByTestId('mock-skeleton-loader')).toBeInTheDocument()

    // After timeout, skeleton should disappear
    await waitFor(() => {
      expect(screen.queryByTestId('mock-skeleton-loader')).not.toBeInTheDocument()
    }, { timeout: 500 })
  })

  it('Arrow Right moves to next tab', () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...defaultProps} onTabChange={onTabChange} activeTab="participants" />)

    const activeTabEl = screen.getByTestId('sidebar-tab-participants')
    fireEvent.keyDown(activeTabEl, { key: 'ArrowRight' })

    expect(onTabChange).toHaveBeenCalledWith('raise-hand')
  })

  it('Arrow Left wraps to last tab from first', () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...defaultProps} onTabChange={onTabChange} activeTab="participants" />)

    const activeTabEl = screen.getByTestId('sidebar-tab-participants')
    fireEvent.keyDown(activeTabEl, { key: 'ArrowLeft' })

    expect(onTabChange).toHaveBeenCalledWith('transcription')
  })

  it('has accessible aria-label on the sidebar container', () => {
    render(<Sidebar {...defaultProps} />)
    const sidebar = screen.getByTestId('meeting-sidebar')
    expect(sidebar).toHaveAttribute('aria-label', 'Thanh bên cuộc họp')
  })

  it('uses tabIndex 0 for active tab and -1 for inactive tabs (roving tabindex)', () => {
    render(<Sidebar {...defaultProps} activeTab="participants" />)
    const activeTabEl = screen.getByTestId('sidebar-tab-participants')
    const inactiveTabEl = screen.getByTestId('sidebar-tab-raise-hand')

    expect(activeTabEl).toHaveAttribute('tabindex', '0')
    expect(inactiveTabEl).toHaveAttribute('tabindex', '-1')
  })

  it('renders backdrop with md:hidden class for mobile only', () => {
    render(<Sidebar {...defaultProps} />)
    const backdrop = screen.getByTestId('sidebar-backdrop')
    expect(backdrop).toHaveClass('md:hidden')
  })

  it('active tab has aria-controls pointing to panel id', () => {
    render(<Sidebar {...defaultProps} activeTab="participants" />)
    const tab = screen.getByTestId('sidebar-tab-participants')
    expect(tab).toHaveAttribute('aria-controls', 'sidebar-panel-participants')
  })
})
