/**
 * Unit tests for useKeyboardShortcuts hook.
 *
 * Tests keyboard shortcut registration, matching, suppression on input focus,
 * per-shortcut enabled flag, global enabled flag, and cleanup on unmount.
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useKeyboardShortcuts, MEETING_SHORTCUTS } from '../useKeyboardShortcuts'
import type { Shortcut } from '../useKeyboardShortcuts'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function fireKeyDown(options: {
  key: string
  altKey?: boolean
  shiftKey?: boolean
}) {
  const event = new KeyboardEvent('keydown', {
    key: options.key,
    altKey: options.altKey ?? false,
    shiftKey: options.shiftKey ?? false,
    bubbles: true,
    cancelable: true,
  })
  document.dispatchEvent(event)
  return event
}

function createShortcut(overrides: Partial<Shortcut> = {}): Shortcut {
  return {
    key: 's',
    altKey: true,
    action: vi.fn(),
    description: 'Test shortcut',
    enabled: true,
    ...overrides,
  }
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('useKeyboardShortcuts — shortcut matching', () => {
  it('should invoke action when key and altKey match', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 's', altKey: true })

    expect(action).toHaveBeenCalledTimes(1)
  })

  it('should not invoke action when key does not match', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 'h', altKey: true })

    expect(action).not.toHaveBeenCalled()
  })

  it('should not invoke action when altKey does not match', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 's', altKey: false })

    expect(action).not.toHaveBeenCalled()
  })

  it('should match shiftKey when specified in shortcut', () => {
    const action = vi.fn()
    const shortcuts = [
      createShortcut({ key: '?', altKey: true, shiftKey: true, action }),
    ]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: '?', altKey: true, shiftKey: true })

    expect(action).toHaveBeenCalledTimes(1)
  })

  it('should not match when shiftKey is required but not pressed', () => {
    const action = vi.fn()
    const shortcuts = [
      createShortcut({ key: '?', altKey: true, shiftKey: true, action }),
    ]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: '?', altKey: true, shiftKey: false })

    expect(action).not.toHaveBeenCalled()
  })

  it('should match case-insensitively', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 'S', altKey: true })

    expect(action).toHaveBeenCalledTimes(1)
  })

  it('should ignore shiftKey when not specified in shortcut', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    // shiftKey is pressed but shortcut doesn't specify shiftKey requirement
    fireKeyDown({ key: 's', altKey: true, shiftKey: true })

    expect(action).toHaveBeenCalledTimes(1)
  })

  it('should invoke only the first matching shortcut', () => {
    const action1 = vi.fn()
    const action2 = vi.fn()
    const shortcuts = [
      createShortcut({ key: 's', altKey: true, action: action1 }),
      createShortcut({ key: 's', altKey: true, action: action2 }),
    ]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 's', altKey: true })

    expect(action1).toHaveBeenCalledTimes(1)
    expect(action2).not.toHaveBeenCalled()
  })
})

describe('useKeyboardShortcuts — global enabled flag', () => {
  it('should not invoke action when globally disabled', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: false }))

    fireKeyDown({ key: 's', altKey: true })

    expect(action).not.toHaveBeenCalled()
  })

  it('should invoke action when globally enabled', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 's', altKey: true })

    expect(action).toHaveBeenCalledTimes(1)
  })
})

describe('useKeyboardShortcuts — per-shortcut enabled flag', () => {
  it('should not invoke action when individual shortcut is disabled', () => {
    const action = vi.fn()
    const shortcuts = [
      createShortcut({ key: 's', altKey: true, action, enabled: false }),
    ]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 's', altKey: true })

    expect(action).not.toHaveBeenCalled()
  })

  it('should skip disabled shortcut and match next enabled one', () => {
    const action1 = vi.fn()
    const action2 = vi.fn()
    const shortcuts = [
      createShortcut({ key: 's', altKey: true, action: action1, enabled: false }),
      createShortcut({ key: 's', altKey: true, action: action2, enabled: true }),
    ]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    fireKeyDown({ key: 's', altKey: true })

    expect(action1).not.toHaveBeenCalled()
    expect(action2).toHaveBeenCalledTimes(1)
  })
})

describe('useKeyboardShortcuts — input suppression', () => {
  let inputElement: HTMLInputElement
  let textareaElement: HTMLTextAreaElement
  let contenteditableElement: HTMLDivElement

  beforeEach(() => {
    inputElement = document.createElement('input')
    textareaElement = document.createElement('textarea')
    contenteditableElement = document.createElement('div')
    contenteditableElement.setAttribute('contenteditable', 'true')
    document.body.appendChild(inputElement)
    document.body.appendChild(textareaElement)
    document.body.appendChild(contenteditableElement)
  })

  afterEach(() => {
    document.body.removeChild(inputElement)
    document.body.removeChild(textareaElement)
    document.body.removeChild(contenteditableElement)
  })

  it('should suppress shortcut when focused on input element', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    inputElement.focus()
    fireKeyDown({ key: 's', altKey: true })

    expect(action).not.toHaveBeenCalled()
  })

  it('should suppress shortcut when focused on textarea element', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    textareaElement.focus()
    fireKeyDown({ key: 's', altKey: true })

    expect(action).not.toHaveBeenCalled()
  })

  it('should suppress shortcut when focused on contenteditable element', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    contenteditableElement.focus()
    fireKeyDown({ key: 's', altKey: true })

    expect(action).not.toHaveBeenCalled()
  })

  it('should not suppress shortcut when focused on a regular button', () => {
    const button = document.createElement('button')
    document.body.appendChild(button)

    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    renderHook(() => useKeyboardShortcuts({ shortcuts, enabled: true }))

    button.focus()
    fireKeyDown({ key: 's', altKey: true })

    expect(action).toHaveBeenCalledTimes(1)

    document.body.removeChild(button)
  })
})

describe('useKeyboardShortcuts — cleanup', () => {
  it('should remove keydown listener on unmount', () => {
    const action = vi.fn()
    const shortcuts = [createShortcut({ key: 's', altKey: true, action })]

    const { unmount } = renderHook(() =>
      useKeyboardShortcuts({ shortcuts, enabled: true })
    )

    unmount()

    fireKeyDown({ key: 's', altKey: true })

    expect(action).not.toHaveBeenCalled()
  })

  it('should update listener when shortcuts change', () => {
    const action1 = vi.fn()
    const action2 = vi.fn()

    const { rerender } = renderHook(
      ({ shortcuts, enabled }) => useKeyboardShortcuts({ shortcuts, enabled }),
      {
        initialProps: {
          shortcuts: [createShortcut({ key: 's', altKey: true, action: action1 })],
          enabled: true,
        },
      }
    )

    fireKeyDown({ key: 's', altKey: true })
    expect(action1).toHaveBeenCalledTimes(1)

    rerender({
      shortcuts: [createShortcut({ key: 'h', altKey: true, action: action2 })],
      enabled: true,
    })

    fireKeyDown({ key: 's', altKey: true })
    expect(action1).toHaveBeenCalledTimes(1) // not called again

    fireKeyDown({ key: 'h', altKey: true })
    expect(action2).toHaveBeenCalledTimes(1)
  })
})

describe('MEETING_SHORTCUTS — registry constant', () => {
  it('should contain 4 shortcut definitions', () => {
    expect(MEETING_SHORTCUTS).toHaveLength(4)
  })

  it('should include Alt+S for toggle sidebar', () => {
    const shortcut = MEETING_SHORTCUTS.find((s) => s.key === 's')
    expect(shortcut).toBeDefined()
    expect(shortcut!.altKey).toBe(true)
    expect(shortcut!.description).toBe('Toggle sidebar')
  })

  it('should include Alt+H for raise/lower hand', () => {
    const shortcut = MEETING_SHORTCUTS.find((s) => s.key === 'h')
    expect(shortcut).toBeDefined()
    expect(shortcut!.altKey).toBe(true)
    expect(shortcut!.description).toBe('Raise/lower hand')
  })

  it('should include Alt+T for transcription tab', () => {
    const shortcut = MEETING_SHORTCUTS.find((s) => s.key === 't')
    expect(shortcut).toBeDefined()
    expect(shortcut!.altKey).toBe(true)
    expect(shortcut!.description).toBe('Switch to transcription tab')
  })

  it('should include Alt+Shift+? for shortcuts help', () => {
    const shortcut = MEETING_SHORTCUTS.find((s) => s.key === '?')
    expect(shortcut).toBeDefined()
    expect(shortcut!.altKey).toBe(true)
    expect(shortcut!.shiftKey).toBe(true)
    expect(shortcut!.description).toBe('Show shortcuts help')
  })
})
