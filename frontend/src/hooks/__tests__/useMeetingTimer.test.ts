/**
 * Unit tests for useMeetingTimer hook.
 *
 * Tests elapsed time calculation, 1-second interval updates,
 * formatting via formatElapsedTime, and cleanup on unmount.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useMeetingTimer } from '../useMeetingTimer'

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useMeetingTimer — initial state', () => {
  it('should return 0 elapsed seconds when joinedAt is now', () => {
    const now = new Date().toISOString()
    const { result } = renderHook(() => useMeetingTimer(now))

    expect(result.current.elapsedSeconds).toBe(0)
    expect(result.current.formatted).toBe('00:00')
  })

  it('should calculate initial elapsed seconds from joinedAt in the past', () => {
    const fiveMinutesAgo = new Date(Date.now() - 5 * 60 * 1000).toISOString()
    const { result } = renderHook(() => useMeetingTimer(fiveMinutesAgo))

    expect(result.current.elapsedSeconds).toBe(300)
    expect(result.current.formatted).toBe('05:00')
  })

  it('should return 0 for future joinedAt timestamps', () => {
    const inFuture = new Date(Date.now() + 60000).toISOString()
    const { result } = renderHook(() => useMeetingTimer(inFuture))

    expect(result.current.elapsedSeconds).toBe(0)
    expect(result.current.formatted).toBe('00:00')
  })
})

describe('useMeetingTimer — interval updates', () => {
  it('should update elapsed seconds every second', () => {
    const now = new Date().toISOString()
    const { result } = renderHook(() => useMeetingTimer(now))

    expect(result.current.elapsedSeconds).toBe(0)

    act(() => {
      vi.advanceTimersByTime(1000)
    })

    expect(result.current.elapsedSeconds).toBe(1)
    expect(result.current.formatted).toBe('00:01')

    act(() => {
      vi.advanceTimersByTime(1000)
    })

    expect(result.current.elapsedSeconds).toBe(2)
    expect(result.current.formatted).toBe('00:02')
  })

  it('should accumulate time correctly over multiple seconds', () => {
    const now = new Date().toISOString()
    const { result } = renderHook(() => useMeetingTimer(now))

    act(() => {
      vi.advanceTimersByTime(65000) // 65 seconds
    })

    expect(result.current.elapsedSeconds).toBe(65)
    expect(result.current.formatted).toBe('01:05')
  })

  it('should display HH:MM:SS format after 1 hour', () => {
    const oneHourAgo = new Date(Date.now() - 3600 * 1000).toISOString()
    const { result } = renderHook(() => useMeetingTimer(oneHourAgo))

    expect(result.current.elapsedSeconds).toBe(3600)
    expect(result.current.formatted).toBe('01:00:00')

    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(result.current.elapsedSeconds).toBe(3605)
    expect(result.current.formatted).toBe('01:00:05')
  })
})

describe('useMeetingTimer — cleanup', () => {
  it('should clear interval on unmount', () => {
    const clearIntervalSpy = vi.spyOn(global, 'clearInterval')
    const now = new Date().toISOString()
    const { unmount } = renderHook(() => useMeetingTimer(now))

    unmount()

    expect(clearIntervalSpy).toHaveBeenCalled()
    clearIntervalSpy.mockRestore()
  })

  it('should not update state after unmount', () => {
    const now = new Date().toISOString()
    const { result, unmount } = renderHook(() => useMeetingTimer(now))

    const elapsedBeforeUnmount = result.current.elapsedSeconds
    unmount()

    act(() => {
      vi.advanceTimersByTime(5000)
    })

    // After unmount, the last captured value should remain unchanged
    expect(result.current.elapsedSeconds).toBe(elapsedBeforeUnmount)
  })
})

describe('useMeetingTimer — joinedAt changes', () => {
  it('should recalculate when joinedAt prop changes', () => {
    const tenMinutesAgo = new Date(Date.now() - 10 * 60 * 1000).toISOString()
    const { result, rerender } = renderHook(
      ({ joinedAt }) => useMeetingTimer(joinedAt),
      { initialProps: { joinedAt: tenMinutesAgo } }
    )

    expect(result.current.elapsedSeconds).toBe(600)

    // Change to 2 minutes ago
    const twoMinutesAgo = new Date(Date.now() - 2 * 60 * 1000).toISOString()
    rerender({ joinedAt: twoMinutesAgo })

    expect(result.current.elapsedSeconds).toBe(120)
    expect(result.current.formatted).toBe('02:00')
  })
})
