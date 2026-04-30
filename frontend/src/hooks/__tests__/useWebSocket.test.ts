/**
 * Unit tests for useWebSocket hook.
 *
 * Strategy: mock @stomp/stompjs Client and sockjs-client so no real network
 * connections are made. We capture the callbacks passed to the Client
 * constructor and invoke them manually to simulate connect/disconnect/error
 * scenarios.
 *
 * Requirements: 20.3
 */

import { describe, it, expect, vi, beforeEach, afterEach, type Mock } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useWebSocket } from '../useWebSocket'
import useAuthStore from '../../store/authStore'
import type { User } from '../../types/user'

// ─── Mock @stomp/stompjs ──────────────────────────────────────────────────────

// Captured constructor options from the most recent Client instantiation
let capturedClientOptions: Record<string, unknown> = {}

// Subscription mock returned by client.subscribe()
const mockSubscription = { unsubscribe: vi.fn() }

// Mock Client instance
const mockClientInstance = {
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn(() => mockSubscription),
}

vi.mock('@stomp/stompjs', () => ({
  Client: vi.fn((options: Record<string, unknown>) => {
    capturedClientOptions = options
    return mockClientInstance
  }),
}))

// ─── Mock sockjs-client ───────────────────────────────────────────────────────

vi.mock('sockjs-client', () => ({
  default: vi.fn(() => ({})),
}))

// ─── Mock authStore ───────────────────────────────────────────────────────────

const mockToken = 'test.jwt.token'
const mockUser: User = { id: 1, username: 'testuser', email: 'test@example.com', role: 'USER' }

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Simulate the STOMP client successfully connecting */
function simulateConnect() {
  act(() => {
    const onConnect = capturedClientOptions.onConnect as () => void
    onConnect?.()
  })
}

/** Simulate the STOMP client disconnecting */
function simulateDisconnect() {
  act(() => {
    const onDisconnect = capturedClientOptions.onDisconnect as () => void
    onDisconnect?.()
  })
}

/** Simulate a STOMP error frame */
function simulateStompError() {
  act(() => {
    const onStompError = capturedClientOptions.onStompError as (frame: unknown) => void
    onStompError?.({ headers: { message: 'test error' }, body: '' })
  })
}

/** Simulate a WebSocket-level error */
function simulateWebSocketError() {
  act(() => {
    const onWebSocketError = capturedClientOptions.onWebSocketError as (event: unknown) => void
    onWebSocketError?.({ type: 'error' })
  })
}

/** Simulate a message arriving on a subscribed topic */
function simulateMessage(topicIndex: number, body: string) {
  act(() => {
    const call = (mockClientInstance.subscribe as Mock).mock.calls[topicIndex]
    const callback = call?.[1] as ((msg: { body: string }) => void) | undefined
    callback?.({ body })
  })
}

// ─── Setup / teardown ─────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks()
  capturedClientOptions = {}
  mockSubscription.unsubscribe.mockClear()
  mockClientInstance.activate.mockClear()
  mockClientInstance.deactivate.mockClear()
  mockClientInstance.subscribe.mockClear()
  mockClientInstance.subscribe.mockReturnValue(mockSubscription)

  // Ensure the auth store has a token so the hook will connect
  useAuthStore.getState().login(mockToken, mockUser)
})

afterEach(() => {
  useAuthStore.getState().logout()
  vi.useRealTimers()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useWebSocket — initial connection', () => {
  it('should activate the STOMP client on mount', () => {
    renderHook(() => useWebSocket({ meetingId: null }))
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(1)
  })

  it('should pass JWT token in STOMP connect headers', () => {
    renderHook(() => useWebSocket({ meetingId: null }))
    expect(capturedClientOptions.connectHeaders).toEqual({
      Authorization: `Bearer ${mockToken}`,
    })
  })

  it('should NOT connect when there is no JWT token', () => {
    useAuthStore.getState().logout()
    renderHook(() => useWebSocket({ meetingId: null }))
    expect(mockClientInstance.activate).not.toHaveBeenCalled()
  })

  it('should deactivate the client on unmount', () => {
    const { unmount } = renderHook(() => useWebSocket({ meetingId: null }))
    unmount()
    expect(mockClientInstance.deactivate).toHaveBeenCalled()
  })
})

describe('useWebSocket — subscriptions', () => {
  it('should subscribe to /user/queue/notifications on connect', () => {
    renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()

    const subscribedTopics = (mockClientInstance.subscribe as Mock).mock.calls.map(
      (call) => call[0],
    )
    expect(subscribedTopics).toContain('/user/queue/notifications')
  })

  it('should subscribe to /topic/meeting/{id} when meetingId is provided', () => {
    renderHook(() => useWebSocket({ meetingId: 42 }))
    simulateConnect()

    const subscribedTopics = (mockClientInstance.subscribe as Mock).mock.calls.map(
      (call) => call[0],
    )
    expect(subscribedTopics).toContain('/topic/meeting/42')
  })

  it('should NOT subscribe to meeting topic when meetingId is null', () => {
    renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()

    const subscribedTopics = (mockClientInstance.subscribe as Mock).mock.calls.map(
      (call) => call[0],
    )
    const meetingTopics = subscribedTopics.filter((t: string) => t.startsWith('/topic/meeting/'))
    expect(meetingTopics).toHaveLength(0)
  })
})

describe('useWebSocket — onConnected / onDisconnected callbacks', () => {
  it('should call onConnected when STOMP connects', () => {
    const onConnected = vi.fn()
    renderHook(() => useWebSocket({ meetingId: null, onConnected }))
    simulateConnect()
    expect(onConnected).toHaveBeenCalledTimes(1)
  })

  it('should call onDisconnected when STOMP disconnects', () => {
    const onDisconnected = vi.fn()
    renderHook(() => useWebSocket({ meetingId: null, onDisconnected }))
    simulateConnect()
    simulateDisconnect()
    expect(onDisconnected).toHaveBeenCalledTimes(1)
  })
})

describe('useWebSocket — meeting event callback', () => {
  it('should call onMeetingEvent when a message arrives on the meeting topic', () => {
    const onMeetingEvent = vi.fn()
    renderHook(() => useWebSocket({ meetingId: 10, onMeetingEvent }))
    simulateConnect()

    const eventPayload = {
      type: 'MODE_CHANGED',
      meetingId: 10,
      timestamp: '2025-01-01T10:00:00+07:00',
      payload: { mode: 'MEETING_MODE' },
    }
    // subscribe calls: [0] = notifications, [1] = meeting topic
    simulateMessage(1, JSON.stringify(eventPayload))

    expect(onMeetingEvent).toHaveBeenCalledWith(eventPayload)
  })

  it('should not crash when meeting event JSON is malformed', () => {
    const onMeetingEvent = vi.fn()
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderHook(() => useWebSocket({ meetingId: 10, onMeetingEvent }))
    simulateConnect()

    simulateMessage(1, 'not-valid-json{{{')

    expect(onMeetingEvent).not.toHaveBeenCalled()
    expect(consoleSpy).toHaveBeenCalled()
    consoleSpy.mockRestore()
  })
})

describe('useWebSocket — notification callback', () => {
  it('should call onNotification when a message arrives on the notification queue', () => {
    const onNotification = vi.fn()
    renderHook(() => useWebSocket({ meetingId: null, onNotification }))
    simulateConnect()

    const notification = {
      id: 1,
      title: 'Test',
      message: 'Hello',
      type: 'GENERAL',
      read: false,
      createdAt: '2025-01-01T10:00:00+07:00',
    }
    // subscribe calls: [0] = notifications
    simulateMessage(0, JSON.stringify(notification))

    expect(onNotification).toHaveBeenCalledWith(notification)
  })

  it('should not crash when notification JSON is malformed', () => {
    const onNotification = vi.fn()
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    renderHook(() => useWebSocket({ meetingId: null, onNotification }))
    simulateConnect()

    simulateMessage(0, '{{invalid')

    expect(onNotification).not.toHaveBeenCalled()
    expect(consoleSpy).toHaveBeenCalled()
    consoleSpy.mockRestore()
  })
})

describe('useWebSocket — exponential backoff reconnect', () => {
  it('should schedule reconnect after disconnect', () => {
    vi.useFakeTimers()
    renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()
    simulateDisconnect()

    // After 1s (first backoff delay), a new activate should be called
    act(() => { vi.advanceTimersByTime(1_000) })
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(2)
  })

  it('should use increasing delays on consecutive disconnects without reconnect', () => {
    vi.useFakeTimers()
    renderHook(() => useWebSocket({ meetingId: null }))

    // First connect then immediately disconnect (attempt 0 → schedules 1s)
    simulateConnect()
    simulateDisconnect()

    // Advance 1s → reconnect fires (attempt 1 → schedules 2s on next disconnect)
    act(() => { vi.advanceTimersByTime(1_000) })
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(2)

    // Disconnect again WITHOUT a successful connect in between
    // reconnectAttemptRef is now 1, so next delay = 2s
    simulateDisconnect()

    // Advance only 1s — 2s backoff has NOT elapsed yet
    act(() => { vi.advanceTimersByTime(1_000) })
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(2)

    // Advance the remaining 1s — 2s backoff completes
    act(() => { vi.advanceTimersByTime(1_000) })
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(3)
  })

  it('should reset backoff counter after successful reconnect', () => {
    vi.useFakeTimers()
    renderHook(() => useWebSocket({ meetingId: null }))

    simulateConnect()
    simulateDisconnect()
    act(() => { vi.advanceTimersByTime(1_000) })
    // Reconnect succeeds
    simulateConnect()

    // Disconnect again — should use 1s delay again (reset)
    simulateDisconnect()
    act(() => { vi.advanceTimersByTime(1_000) })
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(3)
  })

  it('should schedule reconnect after STOMP error', () => {
    vi.useFakeTimers()
    renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()
    simulateStompError()

    act(() => { vi.advanceTimersByTime(1_000) })
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(2)
  })

  it('should schedule reconnect after WebSocket error', () => {
    vi.useFakeTimers()
    renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()
    simulateWebSocketError()

    act(() => { vi.advanceTimersByTime(1_000) })
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(2)
  })

  it('should NOT reconnect after manual disconnect()', () => {
    vi.useFakeTimers()
    const { result } = renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()

    act(() => { result.current.disconnect() })

    // Advance well past all backoff delays
    act(() => { vi.advanceTimersByTime(60_000) })
    // Only the initial activate — no reconnect
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(1)
  })
})

describe('useWebSocket — manual reconnect()', () => {
  it('should reconnect immediately when reconnect() is called', () => {
    const { result } = renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()
    simulateDisconnect()

    act(() => { result.current.reconnect() })
    // Initial activate + reconnect activate
    expect(mockClientInstance.activate).toHaveBeenCalledTimes(2)
  })
})

describe('useWebSocket — cleanup on unmount', () => {
  it('should unsubscribe all subscriptions on unmount', () => {
    const { unmount } = renderHook(() => useWebSocket({ meetingId: 5 }))
    simulateConnect()

    unmount()

    expect(mockSubscription.unsubscribe).toHaveBeenCalled()
  })

  it('should deactivate the client on unmount', () => {
    const { unmount } = renderHook(() => useWebSocket({ meetingId: null }))
    simulateConnect()

    unmount()

    expect(mockClientInstance.deactivate).toHaveBeenCalled()
  })
})
