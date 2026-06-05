/**
 * Unit tests for ToastContainer component.
 *
 * Tests:
 * - Renders toast notifications from the store
 * - Limits visible toasts to maxVisible prop
 * - Includes aria-live region for screen reader announcements
 * - Positions in bottom-left corner (CSS classes)
 * - Dismiss button removes toast
 * - Enter animation classes are applied
 *
 * Requirements: 5.4, 5.5, 5.6, 5.7
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen, act, fireEvent } from '@testing-library/react'
import ToastContainer from '../ToastContainer'
import useToastStore from '../../../store/toastStore'

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.useFakeTimers()
  // Reset the toast store before each test
  act(() => {
    useToastStore.getState().clearAll()
  })
})

afterEach(() => {
  vi.useRealTimers()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ToastContainer', () => {
  it('renders the container with correct positioning classes', () => {
    render(<ToastContainer />)

    const container = screen.getByTestId('toast-container')
    expect(container).toBeInTheDocument()
    expect(container).toHaveClass('absolute', 'bottom-16', 'left-4')
  })

  it('renders toast items from the store', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Alice raised their hand',
        icon: '✋',
        type: 'info',
      })
    })

    render(<ToastContainer />)

    const toasts = useToastStore.getState().toasts
    const toastEl = screen.getByTestId(`toast-item-${toasts[0].id}`)
    expect(toastEl).toBeInTheDocument()
    expect(toastEl).toHaveTextContent('Alice raised their hand')
    expect(toastEl).toHaveTextContent('✋')
  })

  it('limits visible toasts to maxVisible prop', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Toast 1',
        icon: '✋',
        type: 'info',
      })
      useToastStore.getState().addToast({
        message: 'Toast 2',
        icon: '🎤',
        type: 'success',
      })
      useToastStore.getState().addToast({
        message: 'Toast 3',
        icon: '🔇',
        type: 'warning',
      })
    })

    render(<ToastContainer maxVisible={2} />)

    const container = screen.getByTestId('toast-container')
    const toastItems = container.querySelectorAll('[data-testid^="toast-item-"]')
    expect(toastItems).toHaveLength(2)
    // Only the last 2 should be visible
    expect(container).not.toHaveTextContent('Toast 1')
    expect(toastItems[0]).toHaveTextContent('Toast 2')
    expect(toastItems[1]).toHaveTextContent('Toast 3')
  })

  it('defaults maxVisible to 3', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Toast 1',
        icon: '✋',
        type: 'info',
      })
      useToastStore.getState().addToast({
        message: 'Toast 2',
        icon: '🎤',
        type: 'success',
      })
      useToastStore.getState().addToast({
        message: 'Toast 3',
        icon: '🔇',
        type: 'warning',
      })
    })

    render(<ToastContainer />)

    const container = screen.getByTestId('toast-container')
    const toastItems = container.querySelectorAll('[data-testid^="toast-item-"]')
    expect(toastItems).toHaveLength(3)
    expect(toastItems[0]).toHaveTextContent('Toast 1')
    expect(toastItems[1]).toHaveTextContent('Toast 2')
    expect(toastItems[2]).toHaveTextContent('Toast 3')
  })

  it('includes aria-live region for screen reader announcements', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Bob was granted speaking permission',
        icon: '🎤',
        type: 'success',
      })
    })

    render(<ToastContainer />)

    const ariaLive = screen.getByTestId('toast-aria-live')
    expect(ariaLive).toHaveAttribute('aria-live', 'polite')
    expect(ariaLive).toHaveAttribute('aria-atomic', 'true')
    expect(ariaLive).toHaveTextContent('Bob was granted speaking permission')
  })

  it('announces the most recent toast in the aria-live region', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'First toast',
        icon: '✋',
        type: 'info',
      })
      useToastStore.getState().addToast({
        message: 'Second toast',
        icon: '🎤',
        type: 'success',
      })
    })

    render(<ToastContainer />)

    const ariaLive = screen.getByTestId('toast-aria-live')
    expect(ariaLive).toHaveTextContent('Second toast')
  })

  it('dismiss button removes the toast', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Dismissible toast',
        icon: '✋',
        type: 'info',
      })
    })

    render(<ToastContainer />)

    const container = screen.getByTestId('toast-container')
    const toastItems = container.querySelectorAll('[data-testid^="toast-item-"]')
    expect(toastItems).toHaveLength(1)

    const dismissButton = screen.getByLabelText('Dismiss notification')
    fireEvent.click(dismissButton)

    const toastItemsAfter = container.querySelectorAll('[data-testid^="toast-item-"]')
    expect(toastItemsAfter).toHaveLength(0)
  })

  it('renders empty when no toasts are present', () => {
    render(<ToastContainer />)

    const container = screen.getByTestId('toast-container')
    // Only the aria-live region should be present (no toast items)
    const ariaLive = screen.getByTestId('toast-aria-live')
    expect(ariaLive).toHaveTextContent('')
    expect(container.querySelectorAll('[data-testid^="toast-item-"]')).toHaveLength(0)
  })

  it('applies correct border color based on toast type', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Success toast',
        icon: '🎤',
        type: 'success',
      })
    })

    render(<ToastContainer />)

    const toasts = useToastStore.getState().toasts
    const toastEl = screen.getByTestId(`toast-item-${toasts[0].id}`)
    expect(toastEl).toHaveClass('border-green-600')
  })

  it('applies transition classes for animation', () => {
    act(() => {
      useToastStore.getState().addToast({
        message: 'Animated toast',
        icon: '✋',
        type: 'info',
      })
    })

    render(<ToastContainer />)

    const toasts = useToastStore.getState().toasts
    const toastEl = screen.getByTestId(`toast-item-${toasts[0].id}`)
    expect(toastEl).toHaveClass('transition-all', 'duration-300', 'ease-in-out')
  })
})
