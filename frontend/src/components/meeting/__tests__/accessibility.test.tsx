/**
 * Integration-level accessibility tests for the Meeting Room sidebar and toast system.
 *
 * Verifies key accessibility contracts at the component integration level:
 * 1. Sidebar tabs have correct ARIA attributes (role="tablist", role="tab", aria-selected)
 * 2. Tab panels have correct ARIA attributes (role="tabpanel", aria-labelledby)
 * 3. Toast container has aria-live="polite"
 * 4. Focus trap works in overlay mode (Tab wraps)
 * 5. Escape key closes overlay
 *
 * Requirements: 6.5, 8.1, 8.2, 8.3, 8.4, 5.7
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import React from 'react'
import Sidebar, { type SidebarProps, type SidebarTab } from '../Sidebar'
import ToastContainer from '../ToastContainer'
import useToastStore from '../../../store/toastStore'
import { useFocusManagement } from '../../../hooks/useFocusManagement'
import { renderHook } from '@testing-library/react'
import type { Meeting } from '../../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../ParticipantList', () => ({
  default: () => <div data-testid="mock-participant-list"><button>Participant Action</button></div>,
}))

vi.mock('../RaiseHandPanel', () => ({
  default: () => <div data-testid="mock-raise-hand-panel"><button>Raise Hand Action</button></div>,
}))

vi.mock('../TranscriptionPanel', () => ({
  default: () => <div data-testid="mock-transcription-panel"><button>Transcription Action</button></div>,
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

const createMeeting = (): Meeting => ({
  id: 1,
  title: 'Accessibility Test Meeting',
  meetingCode: 'MTG-A11Y',
  status: 'ACTIVE',
  mode: 'MEETING_MODE',
  transcriptionPriority: 'HIGH_PRIORITY',
  startTime: '2024-01-01T10:00:00Z',
  endTime: '2024-01-01T11:00:00Z',
  room: { id: 1, name: 'Room A', capacity: 10 },
  hostUser: { id: 1, username: 'host', fullName: 'Host User', email: 'host@test.com', role: 'ADMIN', isActive: true },
  secretaryUser: { id: 2, username: 'secretary', fullName: 'Secretary', email: 'sec@test.com', role: 'SECRETARY', isActive: true },
  createdBy: { id: 1, username: 'host', fullName: 'Host User', email: 'host@test.com', role: 'ADMIN', isActive: true },
} as Meeting)

const createRef = <T,>(): React.RefObject<T> => ({ current: null }) as React.RefObject<T>

function createDefaultSidebarProps(overrides?: Partial<SidebarProps>): SidebarProps {
  return {
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
    ...overrides,
  }
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('Accessibility: Sidebar ARIA tab pattern', () => {
  /**
   * Validates: Requirement 6.1, 6.3, 6.4
   * Tests that the sidebar tab bar and panels follow the WAI-ARIA Tabs pattern.
   */

  it('tablist has role="tablist" with an accessible label', () => {
    render(<Sidebar {...createDefaultSidebarProps()} />)

    const tablist = screen.getByRole('tablist')
    expect(tablist).toBeInTheDocument()
    expect(tablist).toHaveAttribute('aria-label')
    expect(tablist.getAttribute('aria-label')).not.toBe('')
  })

  it('each tab has role="tab" and aria-controls pointing to its panel', () => {
    render(<Sidebar {...createDefaultSidebarProps()} />)

    const tabs = screen.getAllByRole('tab')
    expect(tabs.length).toBeGreaterThanOrEqual(1)

    tabs.forEach((tab) => {
      expect(tab).toHaveAttribute('aria-controls')
      const panelId = tab.getAttribute('aria-controls')
      expect(panelId).toBeTruthy()
    })
  })

  it('exactly one tab has aria-selected="true" and others have "false"', () => {
    render(<Sidebar {...createDefaultSidebarProps({ activeTab: 'participants' })} />)

    const tabs = screen.getAllByRole('tab')
    const selectedTabs = tabs.filter((t) => t.getAttribute('aria-selected') === 'true')
    const unselectedTabs = tabs.filter((t) => t.getAttribute('aria-selected') === 'false')

    expect(selectedTabs).toHaveLength(1)
    expect(unselectedTabs).toHaveLength(tabs.length - 1)
  })

  it('tab panel has role="tabpanel" with aria-labelledby referencing the active tab', () => {
    render(<Sidebar {...createDefaultSidebarProps({ activeTab: 'participants' })} />)

    const panel = screen.getByRole('tabpanel')
    expect(panel).toBeInTheDocument()
    expect(panel).toHaveAttribute('aria-labelledby')

    // The aria-labelledby should reference the active tab's id
    const activeTab = screen.getAllByRole('tab').find(
      (t) => t.getAttribute('aria-selected') === 'true'
    )
    expect(panel.getAttribute('aria-labelledby')).toBe(activeTab?.getAttribute('id'))
  })

  it('active tab has tabIndex=0 and inactive tabs have tabIndex=-1 (roving tabindex)', () => {
    render(<Sidebar {...createDefaultSidebarProps({ activeTab: 'participants' })} />)

    const tabs = screen.getAllByRole('tab')
    const activeTab = tabs.find((t) => t.getAttribute('aria-selected') === 'true')
    const inactiveTabs = tabs.filter((t) => t.getAttribute('aria-selected') === 'false')

    expect(activeTab).toHaveAttribute('tabindex', '0')
    inactiveTabs.forEach((tab) => {
      expect(tab).toHaveAttribute('tabindex', '-1')
    })
  })
})

describe('Accessibility: Tab keyboard navigation (Arrow Left/Right)', () => {
  /**
   * Validates: Requirement 6.5
   * Tests that Arrow Left/Right keys navigate between tabs.
   */

  it('ArrowRight moves focus and selection to the next tab', () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...createDefaultSidebarProps({ onTabChange, activeTab: 'participants' })} />)

    const participantsTab = screen.getByTestId('sidebar-tab-participants')
    fireEvent.keyDown(participantsTab, { key: 'ArrowRight' })

    expect(onTabChange).toHaveBeenCalledWith('raise-hand')
  })

  it('ArrowLeft moves focus and selection to the previous tab', () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...createDefaultSidebarProps({ onTabChange, activeTab: 'raise-hand' })} />)

    const raiseHandTab = screen.getByTestId('sidebar-tab-raise-hand')
    fireEvent.keyDown(raiseHandTab, { key: 'ArrowLeft' })

    expect(onTabChange).toHaveBeenCalledWith('participants')
  })

  it('ArrowRight wraps from last tab to first tab', () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...createDefaultSidebarProps({ onTabChange, activeTab: 'transcription' })} />)

    const transcriptionTab = screen.getByTestId('sidebar-tab-transcription')
    fireEvent.keyDown(transcriptionTab, { key: 'ArrowRight' })

    expect(onTabChange).toHaveBeenCalledWith('participants')
  })

  it('ArrowLeft wraps from first tab to last tab', () => {
    const onTabChange = vi.fn()
    render(<Sidebar {...createDefaultSidebarProps({ onTabChange, activeTab: 'participants' })} />)

    const participantsTab = screen.getByTestId('sidebar-tab-participants')
    fireEvent.keyDown(participantsTab, { key: 'ArrowLeft' })

    expect(onTabChange).toHaveBeenCalledWith('transcription')
  })
})

describe('Accessibility: Focus management on sidebar open/close', () => {
  /**
   * Validates: Requirements 8.1, 8.2
   * Tests that focus moves correctly when the sidebar opens and closes.
   */

  let toggleButton: HTMLButtonElement

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    toggleButton = document.createElement('button')
    toggleButton.textContent = 'Toggle Sidebar'
    document.body.appendChild(toggleButton)
  })

  afterEach(() => {
    vi.useRealTimers()
    if (toggleButton.parentNode) {
      document.body.removeChild(toggleButton)
    }
  })

  it('moves focus to first focusable element when sidebar opens', async () => {
    const container = document.createElement('div')
    const firstButton = document.createElement('button')
    firstButton.textContent = 'First'
    const secondButton = document.createElement('button')
    secondButton.textContent = 'Second'
    container.appendChild(firstButton)
    container.appendChild(secondButton)
    document.body.appendChild(container)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    const { rerender } = renderHook(
      (props) => useFocusManagement(props),
      {
        initialProps: {
          isOpen: false,
          isMobileOverlay: false,
          containerRef,
          returnFocusRef,
        },
      }
    )

    // Open the sidebar
    rerender({
      isOpen: true,
      isMobileOverlay: false,
      containerRef,
      returnFocusRef,
    })

    await vi.advanceTimersByTimeAsync(16)

    expect(document.activeElement).toBe(firstButton)

    document.body.removeChild(container)
  })

  it('returns focus to toggle button when sidebar closes', async () => {
    const container = document.createElement('div')
    const innerButton = document.createElement('button')
    innerButton.textContent = 'Inner'
    container.appendChild(innerButton)
    document.body.appendChild(container)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    const { rerender } = renderHook(
      (props) => useFocusManagement(props),
      {
        initialProps: {
          isOpen: true,
          isMobileOverlay: false,
          containerRef,
          returnFocusRef,
        },
      }
    )

    // Close the sidebar
    rerender({
      isOpen: false,
      isMobileOverlay: false,
      containerRef,
      returnFocusRef,
    })

    await vi.advanceTimersByTimeAsync(16)

    expect(document.activeElement).toBe(toggleButton)

    document.body.removeChild(container)
  })
})

describe('Accessibility: Focus trap in mobile overlay mode', () => {
  /**
   * Validates: Requirements 8.3, 8.4
   * Tests that focus is trapped within the sidebar when in mobile overlay mode,
   * and that Escape key closes the overlay.
   */

  it('Tab wraps focus from last to first element in overlay mode', () => {
    const button1 = document.createElement('button')
    button1.textContent = 'First'
    const button2 = document.createElement('button')
    button2.textContent = 'Middle'
    const button3 = document.createElement('button')
    button3.textContent = 'Last'

    const container = document.createElement('div')
    container.appendChild(button1)
    container.appendChild(button2)
    container.appendChild(button3)
    document.body.appendChild(container)

    const outsideButton = document.createElement('button')
    outsideButton.textContent = 'Outside'
    document.body.appendChild(outsideButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: outsideButton }

    renderHook(() =>
      useFocusManagement({
        isOpen: true,
        isMobileOverlay: true,
        containerRef,
        returnFocusRef,
      })
    )

    // Focus the last element
    button3.focus()
    expect(document.activeElement).toBe(button3)

    // Press Tab — should wrap to first
    const tabEvent = new KeyboardEvent('keydown', {
      key: 'Tab',
      shiftKey: false,
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(tabEvent)

    expect(document.activeElement).toBe(button1)

    document.body.removeChild(container)
    document.body.removeChild(outsideButton)
  })

  it('Shift+Tab wraps focus from first to last element in overlay mode', () => {
    const button1 = document.createElement('button')
    button1.textContent = 'First'
    const button2 = document.createElement('button')
    button2.textContent = 'Last'

    const container = document.createElement('div')
    container.appendChild(button1)
    container.appendChild(button2)
    document.body.appendChild(container)

    const outsideButton = document.createElement('button')
    outsideButton.textContent = 'Outside'
    document.body.appendChild(outsideButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: outsideButton }

    renderHook(() =>
      useFocusManagement({
        isOpen: true,
        isMobileOverlay: true,
        containerRef,
        returnFocusRef,
      })
    )

    // Focus the first element
    button1.focus()
    expect(document.activeElement).toBe(button1)

    // Press Shift+Tab — should wrap to last
    const tabEvent = new KeyboardEvent('keydown', {
      key: 'Tab',
      shiftKey: true,
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(tabEvent)

    expect(document.activeElement).toBe(button2)

    document.body.removeChild(container)
    document.body.removeChild(outsideButton)
  })

  it('Escape key triggers onClose in mobile overlay mode', () => {
    const onClose = vi.fn()
    const button1 = document.createElement('button')
    button1.textContent = 'Inner'

    const container = document.createElement('div')
    container.appendChild(button1)
    document.body.appendChild(container)

    const outsideButton = document.createElement('button')
    outsideButton.textContent = 'Outside'
    document.body.appendChild(outsideButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: outsideButton }

    renderHook(() =>
      useFocusManagement({
        isOpen: true,
        isMobileOverlay: true,
        containerRef,
        returnFocusRef,
        onClose,
      })
    )

    // Press Escape
    const escEvent = new KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(escEvent)

    expect(onClose).toHaveBeenCalledTimes(1)

    document.body.removeChild(container)
    document.body.removeChild(outsideButton)
  })

  it('Escape key does NOT trigger onClose when not in overlay mode', () => {
    const onClose = vi.fn()
    const button1 = document.createElement('button')
    button1.textContent = 'Inner'

    const container = document.createElement('div')
    container.appendChild(button1)
    document.body.appendChild(container)

    const outsideButton = document.createElement('button')
    outsideButton.textContent = 'Outside'
    document.body.appendChild(outsideButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: outsideButton }

    renderHook(() =>
      useFocusManagement({
        isOpen: true,
        isMobileOverlay: false,
        containerRef,
        returnFocusRef,
        onClose,
      })
    )

    const escEvent = new KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(escEvent)

    expect(onClose).not.toHaveBeenCalled()

    document.body.removeChild(container)
    document.body.removeChild(outsideButton)
  })
})

describe('Accessibility: Toast aria-live announcements', () => {
  /**
   * Validates: Requirement 5.7
   * Tests that the toast container uses aria-live for screen reader announcements.
   */

  beforeEach(() => {
    vi.useFakeTimers()
    act(() => {
      useToastStore.getState().clearAll()
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('toast container includes an aria-live="polite" region', () => {
    render(<ToastContainer />)

    const ariaLiveRegion = screen.getByTestId('toast-aria-live')
    expect(ariaLiveRegion).toHaveAttribute('aria-live', 'polite')
    expect(ariaLiveRegion).toHaveAttribute('aria-atomic', 'true')
  })

  it('aria-live region announces the most recent toast message', () => {
    render(<ToastContainer />)

    act(() => {
      useToastStore.getState().addToast({
        message: 'Nguyễn Văn A đã giơ tay',
        icon: '✋',
        type: 'info',
      })
    })

    const ariaLiveRegion = screen.getByTestId('toast-aria-live')
    expect(ariaLiveRegion).toHaveTextContent('Nguyễn Văn A đã giơ tay')
  })

  it('aria-live region updates when a new toast is added', () => {
    render(<ToastContainer />)

    act(() => {
      useToastStore.getState().addToast({
        message: 'First notification',
        icon: '✋',
        type: 'info',
      })
    })

    act(() => {
      useToastStore.getState().addToast({
        message: 'Second notification',
        icon: '🎤',
        type: 'success',
      })
    })

    const ariaLiveRegion = screen.getByTestId('toast-aria-live')
    expect(ariaLiveRegion).toHaveTextContent('Second notification')
  })

  it('aria-live region is visually hidden (sr-only) but accessible to screen readers', () => {
    render(<ToastContainer />)

    const ariaLiveRegion = screen.getByTestId('toast-aria-live')
    expect(ariaLiveRegion).toHaveClass('sr-only')
  })

  it('individual toast items have role="status" for implicit aria-live', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Test toast',
        icon: '✋',
        type: 'info',
      })
    })

    render(<ToastContainer />)

    const toasts = useToastStore.getState().toasts
    const toastItem = screen.getByTestId(`toast-item-${toasts[0].id}`)
    expect(toastItem).toHaveAttribute('role', 'status')
  })
})
