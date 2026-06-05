/**
 * Unit tests for useConnectionQuality hook.
 *
 * Tests polling behavior, quality level mapping, cleanup on unmount,
 * and handling of unavailable Jitsi API.
 *
 * Requirements: 16.1, 16.2, 16.3, 16.4, 16.6
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useConnectionQuality } from '../useConnectionQuality'
import type { UseConnectionQualityOptions } from '../useConnectionQuality'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function createMockJitsiRef(overrides: {
  isReady?: boolean
  _api?: {
    getConnectionQuality?: () => {
      local?: {
        roundTripTime?: number
        packetLoss?: number
        jitterBufferDelay?: number
      }
    }
  }
} = {}) {
  return {
    current: {
      mute: vi.fn(),
      unmute: vi.fn(),
      muteLocal: vi.fn(),
      unmuteLocal: vi.fn(),
      muteAll: vi.fn(),
      isReady: overrides.isReady ?? true,
      _api: overrides._api,
    },
  }
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useConnectionQuality — unavailable state', () => {
  it('should return unavailable when jitsiRef.current is null', () => {
    const jitsiRef = { current: null }
    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef, interval: 5000 })
    )

    expect(result.current.stats).toBeNull()
    expect(result.current.level).toBe('unavailable')
  })

  it('should return unavailable when jitsi is not ready', () => {
    const jitsiRef = createMockJitsiRef({ isReady: false })
    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toBeNull()
    expect(result.current.level).toBe('unavailable')
  })

  it('should return unavailable when API has no getConnectionQuality method', () => {
    const jitsiRef = createMockJitsiRef({ isReady: true, _api: {} })
    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toBeNull()
    expect(result.current.level).toBe('unavailable')
  })
})

describe('useConnectionQuality — polling with stats', () => {
  it('should return good quality for low latency and zero packet loss', () => {
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: {
        getConnectionQuality: () => ({
          local: { roundTripTime: 50, packetLoss: 0 },
        }),
      },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toEqual({ latency: 50, packetLoss: 0 })
    expect(result.current.level).toBe('good')
  })

  it('should return moderate quality for medium latency', () => {
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: {
        getConnectionQuality: () => ({
          local: { roundTripTime: 150, packetLoss: 0 },
        }),
      },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toEqual({ latency: 150, packetLoss: 0 })
    expect(result.current.level).toBe('moderate')
  })

  it('should return poor quality for high latency', () => {
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: {
        getConnectionQuality: () => ({
          local: { roundTripTime: 400, packetLoss: 0 },
        }),
      },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toEqual({ latency: 400, packetLoss: 0 })
    expect(result.current.level).toBe('poor')
  })

  it('should return poor quality for high packet loss', () => {
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: {
        getConnectionQuality: () => ({
          local: { roundTripTime: 50, packetLoss: 10 },
        }),
      },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toEqual({ latency: 50, packetLoss: 10 })
    expect(result.current.level).toBe('poor')
  })

  it('should prefer jitterBufferDelay over roundTripTime for latency', () => {
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: {
        getConnectionQuality: () => ({
          local: { jitterBufferDelay: 200, roundTripTime: 50, packetLoss: 0 },
        }),
      },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toEqual({ latency: 200, packetLoss: 0 })
    expect(result.current.level).toBe('moderate')
  })
})

describe('useConnectionQuality — polling interval', () => {
  it('should use default 5000ms interval', () => {
    const getConnectionQuality = vi.fn(() => ({
      local: { roundTripTime: 50, packetLoss: 0 },
    }))
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: { getConnectionQuality },
    })

    renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'] })
    )

    // Called once immediately on mount
    expect(getConnectionQuality).toHaveBeenCalledTimes(1)

    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(getConnectionQuality).toHaveBeenCalledTimes(2)

    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(getConnectionQuality).toHaveBeenCalledTimes(3)
  })

  it('should use custom interval when provided', () => {
    const getConnectionQuality = vi.fn(() => ({
      local: { roundTripTime: 50, packetLoss: 0 },
    }))
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: { getConnectionQuality },
    })

    renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 2000 })
    )

    // Called once immediately
    expect(getConnectionQuality).toHaveBeenCalledTimes(1)

    act(() => {
      vi.advanceTimersByTime(2000)
    })

    expect(getConnectionQuality).toHaveBeenCalledTimes(2)
  })

  it('should update stats when polling detects changes', () => {
    let callCount = 0
    const getConnectionQuality = vi.fn(() => {
      callCount++
      if (callCount === 1) {
        return { local: { roundTripTime: 50, packetLoss: 0 } }
      }
      return { local: { roundTripTime: 400, packetLoss: 8 } }
    })
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: { getConnectionQuality },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.level).toBe('good')

    act(() => {
      vi.advanceTimersByTime(5000)
    })

    expect(result.current.stats).toEqual({ latency: 400, packetLoss: 8 })
    expect(result.current.level).toBe('poor')
  })
})

describe('useConnectionQuality — cleanup', () => {
  it('should clear interval on unmount', () => {
    const clearIntervalSpy = vi.spyOn(global, 'clearInterval')
    const jitsiRef = createMockJitsiRef({ isReady: true })

    const { unmount } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    unmount()

    expect(clearIntervalSpy).toHaveBeenCalled()
    clearIntervalSpy.mockRestore()
  })

  it('should not poll after unmount', () => {
    const getConnectionQuality = vi.fn(() => ({
      local: { roundTripTime: 50, packetLoss: 0 },
    }))
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: { getConnectionQuality },
    })

    const { unmount } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    // Called once on mount
    expect(getConnectionQuality).toHaveBeenCalledTimes(1)

    unmount()

    act(() => {
      vi.advanceTimersByTime(10000)
    })

    // Should not have been called again after unmount
    expect(getConnectionQuality).toHaveBeenCalledTimes(1)
  })
})

describe('useConnectionQuality — error handling', () => {
  it('should return unavailable when getConnectionQuality throws', () => {
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: {
        getConnectionQuality: () => {
          throw new Error('API error')
        },
      },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toBeNull()
    expect(result.current.level).toBe('unavailable')
  })

  it('should return unavailable when getConnectionQuality returns no local data', () => {
    const jitsiRef = createMockJitsiRef({
      isReady: true,
      _api: {
        getConnectionQuality: () => ({}) as ReturnType<NonNullable<NonNullable<Parameters<typeof createMockJitsiRef>[0]>['_api']>['getConnectionQuality']>,
      },
    })

    const { result } = renderHook(() =>
      useConnectionQuality({ jitsiRef: jitsiRef as unknown as UseConnectionQualityOptions['jitsiRef'], interval: 5000 })
    )

    expect(result.current.stats).toBeNull()
    expect(result.current.level).toBe('unavailable')
  })
})
