/**
 * Unit tests for ConnectionQualityIndicator component.
 *
 * Tests:
 * - Renders 4 green bars for good quality
 * - Renders 2 amber bars for moderate quality
 * - Renders 1 red bar for poor quality
 * - Renders 0 active bars (all gray) when stats are null
 * - Shows tooltip on hover with latency and packet loss
 * - Shows "No data" tooltip when stats are null
 * - Has accessible aria-label with quality level
 *
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6
 */

import { describe, it, expect } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import ConnectionQualityIndicator from '../ConnectionQualityIndicator'

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ConnectionQualityIndicator', () => {
  it('renders 4 signal bars', () => {
    render(<ConnectionQualityIndicator stats={{ latency: 50, packetLoss: 0 }} />)

    for (let i = 1; i <= 4; i++) {
      expect(screen.getByTestId(`signal-bar-${i}`)).toBeInTheDocument()
    }
  })

  it('renders 4 green active bars for good quality', () => {
    render(<ConnectionQualityIndicator stats={{ latency: 50, packetLoss: 0 }} />)

    for (let i = 1; i <= 4; i++) {
      const bar = screen.getByTestId(`signal-bar-${i}`)
      expect(bar).toHaveAttribute('data-active', 'true')
      expect(bar).toHaveClass('bg-green-400')
    }
  })

  it('renders 2 amber active bars for moderate quality', () => {
    render(<ConnectionQualityIndicator stats={{ latency: 150, packetLoss: 2 }} />)

    // First 2 bars are active (amber)
    for (let i = 1; i <= 2; i++) {
      const bar = screen.getByTestId(`signal-bar-${i}`)
      expect(bar).toHaveAttribute('data-active', 'true')
      expect(bar).toHaveClass('bg-amber-400')
    }

    // Last 2 bars are inactive
    for (let i = 3; i <= 4; i++) {
      const bar = screen.getByTestId(`signal-bar-${i}`)
      expect(bar).toHaveAttribute('data-active', 'false')
      expect(bar).toHaveClass('bg-slate-600')
    }
  })

  it('renders 1 red active bar for poor quality', () => {
    render(<ConnectionQualityIndicator stats={{ latency: 500, packetLoss: 10 }} />)

    // First bar is active (red)
    const bar1 = screen.getByTestId('signal-bar-1')
    expect(bar1).toHaveAttribute('data-active', 'true')
    expect(bar1).toHaveClass('bg-red-400')

    // Remaining bars are inactive
    for (let i = 2; i <= 4; i++) {
      const bar = screen.getByTestId(`signal-bar-${i}`)
      expect(bar).toHaveAttribute('data-active', 'false')
      expect(bar).toHaveClass('bg-slate-600')
    }
  })

  it('renders all gray inactive bars when stats are null', () => {
    render(<ConnectionQualityIndicator stats={null} />)

    for (let i = 1; i <= 4; i++) {
      const bar = screen.getByTestId(`signal-bar-${i}`)
      expect(bar).toHaveAttribute('data-active', 'false')
      expect(bar).toHaveClass('bg-slate-600')
    }
  })

  it('shows tooltip with latency and packet loss on hover', () => {
    render(<ConnectionQualityIndicator stats={{ latency: 120, packetLoss: 3 }} />)

    const container = screen.getByTestId('connection-quality-indicator')

    // Tooltip not visible initially
    expect(screen.queryByTestId('connection-quality-tooltip')).not.toBeInTheDocument()

    // Hover to show tooltip
    fireEvent.mouseEnter(container)
    const tooltip = screen.getByTestId('connection-quality-tooltip')
    expect(tooltip).toBeInTheDocument()
    expect(tooltip).toHaveTextContent('Latency: 120ms')
    expect(tooltip).toHaveTextContent('Packet loss: 3%')

    // Mouse leave hides tooltip
    fireEvent.mouseLeave(container)
    expect(screen.queryByTestId('connection-quality-tooltip')).not.toBeInTheDocument()
  })

  it('shows "No data" tooltip when stats are null', () => {
    render(<ConnectionQualityIndicator stats={null} />)

    const container = screen.getByTestId('connection-quality-indicator')
    fireEvent.mouseEnter(container)

    const tooltip = screen.getByTestId('connection-quality-tooltip')
    expect(tooltip).toHaveTextContent('No data')
  })

  it('has accessible aria-label with quality level', () => {
    render(<ConnectionQualityIndicator stats={{ latency: 50, packetLoss: 0 }} />)

    const container = screen.getByTestId('connection-quality-indicator')
    expect(container).toHaveAttribute('aria-label', 'Connection quality: good')
  })

  it('has aria-label "unavailable" when stats are null', () => {
    render(<ConnectionQualityIndicator stats={null} />)

    const container = screen.getByTestId('connection-quality-indicator')
    expect(container).toHaveAttribute('aria-label', 'Connection quality: unavailable')
  })

  it('tooltip has role="tooltip"', () => {
    render(<ConnectionQualityIndicator stats={{ latency: 50, packetLoss: 0 }} />)

    const container = screen.getByTestId('connection-quality-indicator')
    fireEvent.mouseEnter(container)

    const tooltip = screen.getByTestId('connection-quality-tooltip')
    expect(tooltip).toHaveAttribute('role', 'tooltip')
  })
})
