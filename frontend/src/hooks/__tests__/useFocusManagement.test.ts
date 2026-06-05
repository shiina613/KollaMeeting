/**
 * Unit tests for useFocusManagement hook.
 *
 * Tests focus movement on sidebar open/close, focus trap in mobile overlay mode,
 * and Escape key handling.
 *
 * Requirements: 8.1, 8.2, 8.3, 8.4
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useFocusManagement, getFocusableElements } from '../useFocusManagement'
import type { UseFocusManagementOptions } from '../useFocusManagement'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function createContainer(...elements: HTMLElement[]): HTMLDivElement {
  const container = document.createElement('div')
  elements.forEach((el) => container.appendChild(el))
  document.body.appendChild(container)
  return container
}

function createButton(label: string): HTMLButtonElement {
  const button = document.createElement('button')
  button.textContent = label
  return button
}

function createInput(): HTMLInputElement {
  const input = document.createElement('input')
  input.type = 'text'
  return input
}

function fireKeyDown(options: {
  key: string
  shiftKey?: boolean
}) {
  const event = new KeyboardEvent('keydown', {
    key: options.key,
    shiftKey: options.shiftKey ?? false,
    bubbles: true,
    cancelable: true,
  })
  document.dispatchEvent(event)
  return event
}

function createDefaultOptions(overrides: Partial<UseFocusManagementOptions> = {}): UseFocusManagementOptions {
  return {
    isOpen: false,
    isMobileOverlay: false,
    containerRef: { current: null },
    returnFocusRef: { current: null },
    onClose: vi.fn(),
    ...overrides,
  }
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('getFocusableElements', () => {
  let container: HTMLDivElement

  afterEach(() => {
    if (container && container.parentNode) {
      document.body.removeChild(container)
    }
  })

  it('should find buttons, inputs, links, and tabindex elements', () => {
    const button = createButton('Click')
    const input = createInput()
    const link = document.createElement('a')
    link.href = '#'
    link.textContent = 'Link'
    const tabindexDiv = document.createElement('div')
    tabindexDiv.setAttribute('tabindex', '0')

    container = createContainer(button, input, link, tabindexDiv)

    const focusable = getFocusableElements(container)
    expect(focusable).toHaveLength(4)
    expect(focusable).toContain(button)
    expect(focusable).toContain(input)
    expect(focusable).toContain(link)
    expect(focusable).toContain(tabindexDiv)
  })

  it('should exclude disabled elements', () => {
    const button = createButton('Click')
    button.disabled = true
    const input = createInput()
    input.disabled = true

    container = createContainer(button, input)

    const focusable = getFocusableElements(container)
    expect(focusable).toHaveLength(0)
  })

  it('should exclude elements with tabindex="-1"', () => {
    const div = document.createElement('div')
    div.setAttribute('tabindex', '-1')

    container = createContainer(div)

    const focusable = getFocusableElements(container)
    expect(focusable).toHaveLength(0)
  })
})

describe('useFocusManagement — focus on open', () => {
  let container: HTMLDivElement
  let toggleButton: HTMLButtonElement

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (container && container.parentNode) {
      document.body.removeChild(container)
    }
    if (toggleButton && toggleButton.parentNode) {
      document.body.removeChild(toggleButton)
    }
  })

  it('should move focus to first focusable element when sidebar opens', async () => {
    const button1 = createButton('First')
    const button2 = createButton('Second')
    container = createContainer(button1, button2)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    const { rerender } = renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: false,
          containerRef,
          returnFocusRef,
        }),
      }
    )

    // Open the sidebar
    rerender(createDefaultOptions({
      isOpen: true,
      containerRef,
      returnFocusRef,
    }))

    // Wait for requestAnimationFrame
    await vi.advanceTimersByTimeAsync(16)

    expect(document.activeElement).toBe(button1)
  })

  it('should focus container if no focusable elements exist', async () => {
    const textNode = document.createElement('span')
    textNode.textContent = 'No focusable elements'
    container = createContainer(textNode)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    const { rerender } = renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: false,
          containerRef,
          returnFocusRef,
        }),
      }
    )

    rerender(createDefaultOptions({
      isOpen: true,
      containerRef,
      returnFocusRef,
    }))

    await vi.advanceTimersByTimeAsync(16)

    expect(document.activeElement).toBe(container)
    expect(container.getAttribute('tabindex')).toBe('-1')
  })
})

describe('useFocusManagement — focus on close', () => {
  let container: HTMLDivElement
  let toggleButton: HTMLButtonElement

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (container && container.parentNode) {
      document.body.removeChild(container)
    }
    if (toggleButton && toggleButton.parentNode) {
      document.body.removeChild(toggleButton)
    }
  })

  it('should return focus to toggle button when sidebar closes', async () => {
    const button1 = createButton('First')
    container = createContainer(button1)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    const { rerender } = renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          containerRef,
          returnFocusRef,
        }),
      }
    )

    // Close the sidebar
    rerender(createDefaultOptions({
      isOpen: false,
      containerRef,
      returnFocusRef,
    }))

    await vi.advanceTimersByTimeAsync(16)

    expect(document.activeElement).toBe(toggleButton)
  })
})

describe('useFocusManagement — focus trap in mobile overlay', () => {
  let container: HTMLDivElement
  let toggleButton: HTMLButtonElement

  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (container && container.parentNode) {
      document.body.removeChild(container)
    }
    if (toggleButton && toggleButton.parentNode) {
      document.body.removeChild(toggleButton)
    }
  })

  it('should wrap focus from last to first element on Tab', async () => {
    const button1 = createButton('First')
    const button2 = createButton('Second')
    const button3 = createButton('Third')
    container = createContainer(button1, button2, button3)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          isMobileOverlay: true,
          containerRef,
          returnFocusRef,
        }),
      }
    )

    // Focus the last element
    button3.focus()
    expect(document.activeElement).toBe(button3)

    // Press Tab — should wrap to first
    fireKeyDown({ key: 'Tab' })

    expect(document.activeElement).toBe(button1)
  })

  it('should wrap focus from first to last element on Shift+Tab', async () => {
    const button1 = createButton('First')
    const button2 = createButton('Second')
    const button3 = createButton('Third')
    container = createContainer(button1, button2, button3)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          isMobileOverlay: true,
          containerRef,
          returnFocusRef,
        }),
      }
    )

    // Focus the first element
    button1.focus()
    expect(document.activeElement).toBe(button1)

    // Press Shift+Tab — should wrap to last
    fireKeyDown({ key: 'Tab', shiftKey: true })

    expect(document.activeElement).toBe(button3)
  })

  it('should not trap focus when not in mobile overlay mode', () => {
    const button1 = createButton('First')
    const button2 = createButton('Last')
    container = createContainer(button1, button2)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          isMobileOverlay: false,
          containerRef,
          returnFocusRef,
        }),
      }
    )

    // Focus the last element
    button2.focus()
    expect(document.activeElement).toBe(button2)

    // Press Tab — should NOT wrap (no trap active)
    fireKeyDown({ key: 'Tab' })

    // Focus should remain on button2 (browser default would move it, but in jsdom
    // the keydown event alone doesn't move focus — the point is the handler didn't intervene)
    expect(document.activeElement).toBe(button2)
  })
})

describe('useFocusManagement — Escape key', () => {
  let container: HTMLDivElement
  let toggleButton: HTMLButtonElement

  afterEach(() => {
    if (container && container.parentNode) {
      document.body.removeChild(container)
    }
    if (toggleButton && toggleButton.parentNode) {
      document.body.removeChild(toggleButton)
    }
  })

  it('should call onClose when Escape is pressed in mobile overlay mode', () => {
    const onClose = vi.fn()
    const button1 = createButton('First')
    container = createContainer(button1)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          isMobileOverlay: true,
          containerRef,
          returnFocusRef,
          onClose,
        }),
      }
    )

    fireKeyDown({ key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('should not call onClose when Escape is pressed but not in mobile overlay mode', () => {
    const onClose = vi.fn()
    const button1 = createButton('First')
    container = createContainer(button1)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          isMobileOverlay: false,
          containerRef,
          returnFocusRef,
          onClose,
        }),
      }
    )

    fireKeyDown({ key: 'Escape' })

    expect(onClose).not.toHaveBeenCalled()
  })

  it('should not call onClose when sidebar is closed', () => {
    const onClose = vi.fn()
    const button1 = createButton('First')
    container = createContainer(button1)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: false,
          isMobileOverlay: true,
          containerRef,
          returnFocusRef,
          onClose,
        }),
      }
    )

    fireKeyDown({ key: 'Escape' })

    expect(onClose).not.toHaveBeenCalled()
  })
})

describe('useFocusManagement — cleanup', () => {
  let container: HTMLDivElement
  let toggleButton: HTMLButtonElement

  afterEach(() => {
    if (container && container.parentNode) {
      document.body.removeChild(container)
    }
    if (toggleButton && toggleButton.parentNode) {
      document.body.removeChild(toggleButton)
    }
  })

  it('should remove keydown listener on unmount', () => {
    const onClose = vi.fn()
    const button1 = createButton('First')
    container = createContainer(button1)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    const { unmount } = renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          isMobileOverlay: true,
          containerRef,
          returnFocusRef,
          onClose,
        }),
      }
    )

    unmount()

    fireKeyDown({ key: 'Escape' })

    expect(onClose).not.toHaveBeenCalled()
  })

  it('should remove keydown listener when overlay mode is deactivated', () => {
    const onClose = vi.fn()
    const button1 = createButton('First')
    container = createContainer(button1)
    toggleButton = createButton('Toggle')
    document.body.appendChild(toggleButton)

    const containerRef = { current: container }
    const returnFocusRef = { current: toggleButton }

    const { rerender } = renderHook(
      (props: UseFocusManagementOptions) => useFocusManagement(props),
      {
        initialProps: createDefaultOptions({
          isOpen: true,
          isMobileOverlay: true,
          containerRef,
          returnFocusRef,
          onClose,
        }),
      }
    )

    // Deactivate overlay mode
    rerender(createDefaultOptions({
      isOpen: true,
      isMobileOverlay: false,
      containerRef,
      returnFocusRef,
      onClose,
    }))

    fireKeyDown({ key: 'Escape' })

    expect(onClose).not.toHaveBeenCalled()
  })
})
