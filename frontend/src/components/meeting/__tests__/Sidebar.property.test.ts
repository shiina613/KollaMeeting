// Feature: meeting-room-ux-improvements, Property 4: Tab aria-selected exclusivity

/**
 * Property-based test for Sidebar tab aria-selected exclusivity.
 *
 * Property 4: Tab aria-selected exclusivity
 * For any tab selection within the sidebar (given a set of available tabs),
 * exactly one tab SHALL have aria-selected="true" and all other tabs SHALL
 * have aria-selected="false".
 *
 * **Validates: Requirements 6.3**
 */

import { describe, it, expect, vi } from 'vitest'
import * as fc from 'fast-check'
import { render, screen } from '@testing-library/react'
import React from 'react'
import Sidebar, { type SidebarProps, type SidebarTab } from '../Sidebar'
import type { Meeting } from '../../../types/meeting'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../ParticipantList', () => ({
  default: () => React.createElement('div', { 'data-testid': 'mock-participant-list' }),
}))

vi.mock('../RaiseHandPanel', () => ({
  default: (props: { meetingId: number; isHost: boolean }) =>
    React.createElement('div', { 'data-testid': 'mock-raise-hand-panel', 'data-meeting-id': props.meetingId }),
}))

vi.mock('../TranscriptionPanel', () => ({
  default: (props: { meetingId: number }) =>
    React.createElement('div', { 'data-testid': 'mock-transcription-panel', 'data-meeting-id': props.meetingId }),
}))

vi.mock('../SkeletonLoader', () => ({
  default: (props: { rows: number; variant: string }) =>
    React.createElement('div', { 'data-testid': 'mock-skeleton-loader', 'data-rows': props.rows, 'data-variant': props.variant }),
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
} as Meeting)

const createRef = <T,>(): React.RefObject<T> => ({ current: null }) as React.RefObject<T>

// All available tabs when meeting is in MEETING_MODE with HIGH_PRIORITY transcription
// and user is host (sees raise-hand tab)
const ALL_TABS: SidebarTab[] = ['participants', 'raise-hand', 'transcription']

// ─── Property Test ────────────────────────────────────────────────────────────

describe('Property 4: Tab aria-selected exclusivity', () => {
  it('exactly one tab has aria-selected="true" and all others have aria-selected="false" for any selected tab', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: ALL_TABS.length - 1 }),
        (tabIndex) => {
          const selectedTab = ALL_TABS[tabIndex]

          const props: SidebarProps = {
            segments: [],
            isTranscriptionAvailable: true,
            isOpen: true,
            onClose: vi.fn(),
            activeTab: selectedTab,
            onTabChange: vi.fn(),
            meeting: createMeeting(),
            isHost: true,
            isSecretary: false,
            currentUserId: 1,
            sidebarRef: createRef<HTMLDivElement>(),
            toggleButtonRef: createRef<HTMLButtonElement>(),
          }

          const { unmount } = render(React.createElement(Sidebar, props))

          const tabElements = screen.getAllByRole('tab')

          // Count tabs with aria-selected="true"
          const selectedTabs = tabElements.filter(
            (el) => el.getAttribute('aria-selected') === 'true',
          )

          // Count tabs with aria-selected="false"
          const unselectedTabs = tabElements.filter(
            (el) => el.getAttribute('aria-selected') === 'false',
          )

          // Exactly one tab must be selected
          const exactlyOneSelected = selectedTabs.length === 1

          // All other tabs must be explicitly unselected
          const allOthersUnselected = unselectedTabs.length === tabElements.length - 1

          // Total must account for all tabs
          const allAccountedFor = selectedTabs.length + unselectedTabs.length === tabElements.length

          unmount()

          return exactlyOneSelected && allOthersUnselected && allAccountedFor
        },
      ),
      { numRuns: 100 },
    )
  })

  it('the selected tab matches the activeTab prop', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: ALL_TABS.length - 1 }),
        (tabIndex) => {
          const selectedTab = ALL_TABS[tabIndex]

          const props: SidebarProps = {
            segments: [],
            isTranscriptionAvailable: true,
            isOpen: true,
            onClose: vi.fn(),
            activeTab: selectedTab,
            onTabChange: vi.fn(),
            meeting: createMeeting(),
            isHost: true,
            isSecretary: false,
            currentUserId: 1,
            sidebarRef: createRef<HTMLDivElement>(),
            toggleButtonRef: createRef<HTMLButtonElement>(),
          }

          const { unmount } = render(React.createElement(Sidebar, props))

          // The tab with aria-selected="true" should correspond to the activeTab
          const selectedElement = screen.getByTestId(`sidebar-tab-${selectedTab}`)
          const isCorrectTabSelected = selectedElement.getAttribute('aria-selected') === 'true'

          unmount()

          return isCorrectTabSelected
        },
      ),
      { numRuns: 100 },
    )
  })
})
