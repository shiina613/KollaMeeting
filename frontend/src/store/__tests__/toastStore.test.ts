import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import useToastStore from '../toastStore'
import { createToastMessage } from '../toastStore'
import type { MeetingEvent } from '../../types/meeting'

// ─── Reset store and timers before each test ──────────────────────────────────

beforeEach(() => {
  vi.useFakeTimers()
  useToastStore.getState().clearAll()
})

afterEach(() => {
  vi.useRealTimers()
})

// ─── Tests: initial state ─────────────────────────────────────────────────────

describe('toastStore — initial state', () => {
  it('should start with an empty toasts array', () => {
    const state = useToastStore.getState()
    expect(state.toasts).toEqual([])
  })
})

// ─── Tests: addToast ──────────────────────────────────────────────────────────

describe('toastStore — addToast', () => {
  it('should add a toast with generated id and createdAt', () => {
    useToastStore.getState().addToast({
      message: 'Test toast',
      icon: '✋',
      type: 'info',
    })

    const { toasts } = useToastStore.getState()
    expect(toasts).toHaveLength(1)
    expect(toasts[0].message).toBe('Test toast')
    expect(toasts[0].icon).toBe('✋')
    expect(toasts[0].type).toBe('info')
    expect(toasts[0].id).toBeDefined()
    expect(toasts[0].createdAt).toBeGreaterThan(0)
  })

  it('should enforce max 3 toasts by removing oldest', () => {
    const store = useToastStore.getState()
    store.addToast({ message: 'Toast 1', icon: '1', type: 'info' })
    store.addToast({ message: 'Toast 2', icon: '2', type: 'info' })
    store.addToast({ message: 'Toast 3', icon: '3', type: 'info' })
    store.addToast({ message: 'Toast 4', icon: '4', type: 'info' })

    const { toasts } = useToastStore.getState()
    expect(toasts).toHaveLength(3)
    // Oldest (Toast 1) should have been removed
    expect(toasts[0].message).toBe('Toast 2')
    expect(toasts[1].message).toBe('Toast 3')
    expect(toasts[2].message).toBe('Toast 4')
  })

  it('should auto-dismiss toast after 5 seconds', () => {
    useToastStore.getState().addToast({
      message: 'Auto dismiss me',
      icon: '⏰',
      type: 'info',
    })

    expect(useToastStore.getState().toasts).toHaveLength(1)

    vi.advanceTimersByTime(5000)

    expect(useToastStore.getState().toasts).toHaveLength(0)
  })

  it('should not dismiss toast before 5 seconds', () => {
    useToastStore.getState().addToast({
      message: 'Still here',
      icon: '⏰',
      type: 'info',
    })

    vi.advanceTimersByTime(4999)

    expect(useToastStore.getState().toasts).toHaveLength(1)
  })
})

// ─── Tests: removeToast ───────────────────────────────────────────────────────

describe('toastStore — removeToast', () => {
  it('should remove a toast by id', () => {
    useToastStore.getState().addToast({
      message: 'Remove me',
      icon: '❌',
      type: 'warning',
    })

    const { toasts } = useToastStore.getState()
    const id = toasts[0].id

    useToastStore.getState().removeToast(id)

    expect(useToastStore.getState().toasts).toHaveLength(0)
  })

  it('should not throw when removing non-existent id', () => {
    expect(() => {
      useToastStore.getState().removeToast('non-existent-id')
    }).not.toThrow()
  })
})

// ─── Tests: clearAll ──────────────────────────────────────────────────────────

describe('toastStore — clearAll', () => {
  it('should remove all toasts', () => {
    const store = useToastStore.getState()
    store.addToast({ message: 'Toast 1', icon: '1', type: 'info' })
    store.addToast({ message: 'Toast 2', icon: '2', type: 'success' })

    useToastStore.getState().clearAll()

    expect(useToastStore.getState().toasts).toHaveLength(0)
  })
})

// ─── Tests: createToastMessage helper ─────────────────────────────────────────

describe('createToastMessage', () => {
  it('should create toast for RAISE_HAND event with participant name', () => {
    const event: MeetingEvent = {
      type: 'RAISE_HAND',
      meetingId: 1,
      timestamp: '2024-01-01T00:00:00Z',
      payload: { userId: 42, userName: 'Alice', requestedAt: '2024-01-01T00:00:00Z' },
    }

    const result = createToastMessage(event)
    expect(result).not.toBeNull()
    expect(result!.message).toContain('Alice')
    expect(result!.icon).toBe('✋')
    expect(result!.type).toBe('info')
  })

  it('should create toast for SPEAKING_PERMISSION_GRANTED event with participant name', () => {
    const event: MeetingEvent = {
      type: 'SPEAKING_PERMISSION_GRANTED',
      meetingId: 1,
      timestamp: '2024-01-01T00:00:00Z',
      payload: { userId: 42, userName: 'Bob', speakerTurnId: 'turn-1' },
    }

    const result = createToastMessage(event)
    expect(result).not.toBeNull()
    expect(result!.message).toContain('Bob')
    expect(result!.icon).toBe('🎤')
    expect(result!.type).toBe('success')
  })

  it('should create toast for SPEAKING_PERMISSION_REVOKED event', () => {
    const event: MeetingEvent = {
      type: 'SPEAKING_PERMISSION_REVOKED',
      meetingId: 1,
      timestamp: '2024-01-01T00:00:00Z',
      payload: {},
    }

    const result = createToastMessage(event)
    expect(result).not.toBeNull()
    expect(result!.message).toContain('revoked')
    expect(result!.icon).toBe('🔇')
    expect(result!.type).toBe('warning')
  })

  it('should return null for unrelated event types', () => {
    const event: MeetingEvent = {
      type: 'PARTICIPANT_JOINED',
      meetingId: 1,
      timestamp: '2024-01-01T00:00:00Z',
      payload: { userId: 1, userName: 'Charlie' },
    }

    const result = createToastMessage(event)
    expect(result).toBeNull()
  })
})
