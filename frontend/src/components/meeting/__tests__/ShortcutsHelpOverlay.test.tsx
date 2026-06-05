/**
 * Unit tests for ShortcutsHelpOverlay component.
 *
 * Tests:
 * - Does not render when isOpen is false
 * - Renders modal overlay when isOpen is true
 * - Displays all keyboard shortcuts from MEETING_SHORTCUTS registry
 * - Shows shortcut key combinations and descriptions
 * - Closes on Escape key press
 * - Closes on backdrop click
 * - Does not close when clicking inside the dialog
 * - Includes proper ARIA attributes (role="dialog", aria-modal, aria-labelledby)
 *
 * Requirements: 7.5
 */

import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import ShortcutsHelpOverlay from '../ShortcutsHelpOverlay'
import { MEETING_SHORTCUTS } from '../../../hooks/useKeyboardShortcuts'

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('ShortcutsHelpOverlay', () => {
  it('does not render when isOpen is false', () => {
    render(<ShortcutsHelpOverlay isOpen={false} onClose={vi.fn()} />)

    expect(screen.queryByTestId('shortcuts-help-overlay')).not.toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('renders modal overlay when isOpen is true', () => {
    render(<ShortcutsHelpOverlay isOpen={true} onClose={vi.fn()} />)

    expect(screen.getByTestId('shortcuts-help-overlay')).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('displays all keyboard shortcuts from MEETING_SHORTCUTS registry', () => {
    render(<ShortcutsHelpOverlay isOpen={true} onClose={vi.fn()} />)

    const grid = screen.getByTestId('shortcuts-grid')
    for (const shortcut of MEETING_SHORTCUTS) {
      expect(grid).toHaveTextContent(shortcut.description)
    }
  })

  it('shows shortcut key combinations formatted correctly', () => {
    render(<ShortcutsHelpOverlay isOpen={true} onClose={vi.fn()} />)

    const grid = screen.getByTestId('shortcuts-grid')
    // Alt+S → "Alt + S"
    expect(grid).toHaveTextContent('Alt + S')
    // Alt+H → "Alt + H"
    expect(grid).toHaveTextContent('Alt + H')
    // Alt+T → "Alt + T"
    expect(grid).toHaveTextContent('Alt + T')
    // Alt+Shift+? → "Alt + Shift + ?"
    expect(grid).toHaveTextContent('Alt + Shift + ?')
  })

  it('closes on Escape key press', () => {
    const onClose = vi.fn()
    render(<ShortcutsHelpOverlay isOpen={true} onClose={onClose} />)

    fireEvent.keyDown(document, { key: 'Escape' })

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('closes on backdrop click', () => {
    const onClose = vi.fn()
    render(<ShortcutsHelpOverlay isOpen={true} onClose={onClose} />)

    const backdrop = screen.getByTestId('shortcuts-help-overlay')
    fireEvent.click(backdrop)

    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('does not close when clicking inside the dialog', () => {
    const onClose = vi.fn()
    render(<ShortcutsHelpOverlay isOpen={true} onClose={onClose} />)

    const dialog = screen.getByRole('dialog')
    fireEvent.click(dialog)

    expect(onClose).not.toHaveBeenCalled()
  })

  it('includes proper ARIA attributes', () => {
    render(<ShortcutsHelpOverlay isOpen={true} onClose={vi.fn()} />)

    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveAttribute('aria-labelledby', 'shortcuts-help-title')

    const title = screen.getByText('Keyboard Shortcuts')
    expect(title).toHaveAttribute('id', 'shortcuts-help-title')
  })

  it('renders a close button with accessible label', () => {
    const onClose = vi.fn()
    render(<ShortcutsHelpOverlay isOpen={true} onClose={onClose} />)

    const closeButton = screen.getByLabelText('Close shortcuts help')
    expect(closeButton).toBeInTheDocument()

    fireEvent.click(closeButton)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('uses grid layout for shortcuts display', () => {
    render(<ShortcutsHelpOverlay isOpen={true} onClose={vi.fn()} />)

    const grid = screen.getByTestId('shortcuts-grid')
    expect(grid).toHaveClass('grid')
  })
})
