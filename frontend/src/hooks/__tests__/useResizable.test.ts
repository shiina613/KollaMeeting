/**
 * Unit tests for useResizable hook.
 *
 * Tests drag-resize behavior including mouse event handling, clamping,
 * limit detection, and disabled state.
 *
 * Requirements: 1.3, 13.2, 15.1, 15.2, 15.3, 15.4
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useResizable } from '../useResizable'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function createMouseEvent(clientX: number): React.MouseEvent {
  return {
    clientX,
    preventDefault: vi.fn(),
  } as unknown as React.MouseEvent
}

function fireMouseMove(clientX: number) {
  const event = new MouseEvent('mousemove', { clientX })
  document.dispatchEvent(event)
}

function fireMouseUp() {
  const event = new MouseEvent('mouseup')
  document.dispatchEvent(event)
}

const defaultOptions = {
  min: 200,
  max: 600,
  defaultWidth: 288,
  enabled: true,
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useResizable — initial state', () => {
  it('should return the default width on mount', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))
    expect(result.current.width).toBe(288)
  })

  it('should not be dragging initially', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))
    expect(result.current.isDragging).toBe(false)
  })

  it('should not be at limit when default width is between min and max', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))
    expect(result.current.isAtLimit).toBe(false)
  })

  it('should report isAtLimit when defaultWidth equals min', () => {
    const { result } = renderHook(() =>
      useResizable({ ...defaultOptions, defaultWidth: 200 })
    )
    expect(result.current.isAtLimit).toBe(true)
  })

  it('should report isAtLimit when defaultWidth equals max', () => {
    const { result } = renderHook(() =>
      useResizable({ ...defaultOptions, defaultWidth: 600 })
    )
    expect(result.current.isAtLimit).toBe(true)
  })
})

describe('useResizable — drag behavior', () => {
  it('should set isDragging to true on mouse down', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    expect(result.current.isDragging).toBe(true)
  })

  it('should update width on mouse move during drag', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    act(() => {
      fireMouseMove(550)
    })

    // delta = 550 - 500 = 50, new width = 288 + 50 = 338
    expect(result.current.width).toBe(338)
  })

  it('should set isDragging to false on mouse up', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    act(() => {
      fireMouseUp()
    })

    expect(result.current.isDragging).toBe(false)
  })

  it('should not update width after mouse up', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    act(() => {
      fireMouseMove(550)
    })

    act(() => {
      fireMouseUp()
    })

    const widthAfterUp = result.current.width

    act(() => {
      fireMouseMove(600)
    })

    expect(result.current.width).toBe(widthAfterUp)
  })

  it('should prevent default on mouse down', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))
    const event = createMouseEvent(500)

    act(() => {
      result.current.handleMouseDown(event)
    })

    expect(event.preventDefault).toHaveBeenCalled()

    // Cleanup
    act(() => { fireMouseUp() })
  })
})

describe('useResizable — clamping', () => {
  it('should clamp width to min when dragging left past minimum', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    // delta = 0 - 500 = -500, 288 + (-500) = -212, clamped to 200
    act(() => {
      fireMouseMove(0)
    })

    expect(result.current.width).toBe(200)
    expect(result.current.isAtLimit).toBe(true)

    // Cleanup
    act(() => { fireMouseUp() })
  })

  it('should clamp width to max when dragging right past maximum', () => {
    const { result } = renderHook(() => useResizable(defaultOptions))

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    // delta = 1500 - 500 = 1000, 288 + 1000 = 1288, clamped to 600
    act(() => {
      fireMouseMove(1500)
    })

    expect(result.current.width).toBe(600)
    expect(result.current.isAtLimit).toBe(true)

    // Cleanup
    act(() => { fireMouseUp() })
  })
})

describe('useResizable — disabled state', () => {
  it('should not start dragging when enabled is false', () => {
    const { result } = renderHook(() =>
      useResizable({ ...defaultOptions, enabled: false })
    )

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    expect(result.current.isDragging).toBe(false)
  })

  it('should not change width when enabled is false', () => {
    const { result } = renderHook(() =>
      useResizable({ ...defaultOptions, enabled: false })
    )

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    act(() => {
      fireMouseMove(600)
    })

    expect(result.current.width).toBe(288)
  })
})

describe('useResizable — cleanup on unmount', () => {
  it('should remove event listeners on unmount during drag', () => {
    const removeEventListenerSpy = vi.spyOn(document, 'removeEventListener')
    const { result, unmount } = renderHook(() => useResizable(defaultOptions))

    act(() => {
      result.current.handleMouseDown(createMouseEvent(500))
    })

    unmount()

    expect(removeEventListenerSpy).toHaveBeenCalledWith(
      'mousemove',
      expect.any(Function)
    )
    expect(removeEventListenerSpy).toHaveBeenCalledWith(
      'mouseup',
      expect.any(Function)
    )

    removeEventListenerSpy.mockRestore()
  })
})
