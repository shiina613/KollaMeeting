/**
 * ShortcutsHelpOverlay — modal overlay listing all keyboard shortcuts.
 *
 * Displays the available meeting keyboard shortcuts from the MEETING_SHORTCUTS
 * registry in a grid layout. Closes on Escape key press or backdrop click.
 * Includes proper ARIA attributes for accessibility.
 *
 * Requirements: 7.5
 */

import { useCallback, useEffect } from 'react'
import { MEETING_SHORTCUTS } from '../../hooks/useKeyboardShortcuts'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface ShortcutsHelpOverlayProps {
  /** Whether the overlay is visible */
  isOpen: boolean
  /** Callback to close the overlay */
  onClose: () => void
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Formats a shortcut's key combination into a human-readable string.
 * E.g. { key: 's', altKey: true } → "Alt + S"
 *      { key: '?', altKey: true, shiftKey: true } → "Alt + Shift + ?"
 */
function formatKeyCombination(shortcut: (typeof MEETING_SHORTCUTS)[number]): string {
  const parts: string[] = []
  if (shortcut.altKey) parts.push('Alt')
  if ('shiftKey' in shortcut && shortcut.shiftKey) parts.push('Shift')
  parts.push(shortcut.key.toUpperCase())
  return parts.join(' + ')
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * ShortcutsHelpOverlay
 *
 * Modal overlay listing all keyboard shortcuts from the MEETING_SHORTCUTS registry.
 * Closes on Escape key press or backdrop click.
 *
 * Requirements: 7.5
 */
export default function ShortcutsHelpOverlay({ isOpen, onClose }: ShortcutsHelpOverlayProps) {
  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      }
    },
    [onClose]
  )

  useEffect(() => {
    if (!isOpen) return

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [isOpen, handleKeyDown])

  if (!isOpen) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60"
      data-testid="shortcuts-help-overlay"
      onClick={onClose}
      role="presentation"
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="shortcuts-help-title"
        className="w-full max-w-md rounded-lg bg-slate-800 p-6 shadow-xl"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between">
          <h2
            id="shortcuts-help-title"
            className="text-lg font-semibold text-white"
          >
            Keyboard Shortcuts
          </h2>
          <button
            onClick={onClose}
            className="rounded p-1 text-slate-400 hover:bg-slate-700 hover:text-white"
            aria-label="Close shortcuts help"
          >
            <span className="material-symbols-outlined text-[20px]" aria-hidden="true">
              close
            </span>
          </button>
        </div>

        <div
          className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-3"
          data-testid="shortcuts-grid"
        >
          {MEETING_SHORTCUTS.map((shortcut) => (
            <div key={shortcut.key + ('shiftKey' in shortcut && shortcut.shiftKey ? '-shift' : '')} className="contents">
              <kbd className="inline-flex items-center rounded bg-slate-700 px-2 py-1 font-mono text-sm text-slate-200">
                {formatKeyCombination(shortcut)}
              </kbd>
              <span className="flex items-center text-sm text-slate-300">
                {shortcut.description}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
