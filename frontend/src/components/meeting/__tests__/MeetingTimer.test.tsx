/**
 * Unit tests for MeetingTimer component.
 *
 * Tests:
 * - Renders with monospace font class (font-mono)
 * - Displays clock icon alongside formatted time
 * - Updates every second (using fake timers)
 * - Uses useMeetingTimer hook with the provided joinedAt prop
 *
 * Requirements: 4.1, 4.2, 4.5
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import MeetingTimer from '../MeetingTimer'

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('MeetingTimer', () => {
  it('renders with a clock icon', () => {
    const joinedAt = new Date().toISOString()
    render(<MeetingTimer joinedAt={joinedAt} />)

    const icon = screen.getByText('schedule')
    expect(icon).toBeInTheDocument()
    expect(icon).toHaveClass('material-symbols-outlined')
    expect(icon).toHaveAttribute('aria-hidden', 'true')
  })

  it('renders formatted time with monospace font class', () => {
    const joinedAt = new Date().toISOString()
    render(<MeetingTimer joinedAt={joinedAt} />)

    const timerValue = screen.getByTestId('meeting-timer-value')
    expect(timerValue).toHaveClass('font-mono')
    expect(timerValue).toHaveTextContent('00:00')
  })

  it('displays elapsed time based on joinedAt', () => {
    // Set joinedAt to 65 seconds ago
    const now = Date.now()
    vi.setSystemTime(now)
    const joinedAt = new Date(now - 65_000).toISOString()

    render(<MeetingTimer joinedAt={joinedAt} />)

    const timerValue = screen.getByTestId('meeting-timer-value')
    expect(timerValue).toHaveTextContent('01:05')
  })

  it('updates every second', () => {
    const now = Date.now()
    vi.setSystemTime(now)
    const joinedAt = new Date(now).toISOString()

    render(<MeetingTimer joinedAt={joinedAt} />)

    const timerValue = screen.getByTestId('meeting-timer-value')
    expect(timerValue).toHaveTextContent('00:00')

    // Advance 3 seconds
    act(() => {
      vi.advanceTimersByTime(3000)
    })

    expect(timerValue).toHaveTextContent('00:03')
  })

  it('displays HH:MM:SS format for meetings over 1 hour', () => {
    const now = Date.now()
    vi.setSystemTime(now)
    // 1 hour, 5 minutes, 30 seconds ago
    const joinedAt = new Date(now - (3600 + 300 + 30) * 1000).toISOString()

    render(<MeetingTimer joinedAt={joinedAt} />)

    const timerValue = screen.getByTestId('meeting-timer-value')
    expect(timerValue).toHaveTextContent('01:05:30')
  })

  it('has accessible label with current time', () => {
    const now = Date.now()
    vi.setSystemTime(now)
    const joinedAt = new Date(now - 125_000).toISOString()

    render(<MeetingTimer joinedAt={joinedAt} />)

    const container = screen.getByTestId('meeting-timer')
    expect(container).toHaveAttribute('aria-label', 'Thời gian họp: 02:05')
  })

  it('has data-testid on the container', () => {
    const joinedAt = new Date().toISOString()
    render(<MeetingTimer joinedAt={joinedAt} />)

    expect(screen.getByTestId('meeting-timer')).toBeInTheDocument()
  })
})
