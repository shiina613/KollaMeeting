/**
 * useKeyboardShortcuts — registers and manages keyboard shortcuts with input-focus suppression.
 *
 * Handles keydown events on the document, matching registered shortcuts by key,
 * altKey, and shiftKey. Uses `shouldSuppressShortcut` to skip execution when the
 * user is focused on text inputs, textareas, or contenteditable elements.
 *
 * Features:
 * - Registers shortcuts with key/altKey/shiftKey matching
 * - Suppresses shortcuts when focused on input elements
 * - Supports per-shortcut and global enabled flags
 * - Cleans up listener on unmount or when shortcuts change
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */

import { useCallback, useEffect } from 'react'
import { shouldSuppressShortcut } from '../utils/shouldSuppressShortcut'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface Shortcut {
  /** The key to match (case-insensitive) */
  key: string
  /** Whether the Alt key must be held */
  altKey: boolean
  /** Whether the Shift key must be held */
  shiftKey?: boolean
  /** The action to invoke when the shortcut is triggered */
  action: () => void
  /** Human-readable description of the shortcut */
  description: string
  /** Whether this individual shortcut is currently active */
  enabled: boolean
}

export interface UseKeyboardShortcutsOptions {
  /** Array of shortcut definitions to register */
  shortcuts: Shortcut[]
  /** Whether the shortcut system is active (disabled during overlays) */
  enabled: boolean
}

// ─── Shortcut Registry ────────────────────────────────────────────────────────

/**
 * Registry of all meeting keyboard shortcuts.
 * Used by ShortcutsHelpOverlay to display available shortcuts.
 */
export const MEETING_SHORTCUTS = [
  { key: 's', altKey: true, description: 'Toggle sidebar' },
  { key: 'h', altKey: true, description: 'Raise/lower hand' },
  { key: 't', altKey: true, description: 'Switch to transcription tab' },
  { key: '?', altKey: true, shiftKey: true, description: 'Show shortcuts help' },
] as const

// ─── Hook ─────────────────────────────────────────────────────────────────────

/**
 * useKeyboardShortcuts
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5
 */
export function useKeyboardShortcuts(options: UseKeyboardShortcutsOptions): void {
  const { shortcuts, enabled } = options

  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (!enabled) return

      if (shouldSuppressShortcut()) return

      for (const shortcut of shortcuts) {
        if (!shortcut.enabled) continue

        const keyMatches = event.key.toLowerCase() === shortcut.key.toLowerCase()
        const altMatches = event.altKey === shortcut.altKey
        const shiftMatches = shortcut.shiftKey !== undefined
          ? event.shiftKey === shortcut.shiftKey
          : true

        if (keyMatches && altMatches && shiftMatches) {
          event.preventDefault()
          shortcut.action()
          return
        }
      }
    },
    [shortcuts, enabled]
  )

  useEffect(() => {
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [handleKeyDown])
}
