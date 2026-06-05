import { create } from 'zustand'
import type { MeetingEvent, RaiseHandPayload, SpeakingPermissionPayload } from '../types/meeting'

// ─── Toast types ──────────────────────────────────────────────────────────────

export interface Toast {
  id: string
  message: string
  icon: string
  type: 'info' | 'success' | 'warning'
  createdAt: number
}

export interface ToastState {
  toasts: Toast[]
  addToast: (toast: Omit<Toast, 'id' | 'createdAt'>) => void
  removeToast: (id: string) => void
  clearAll: () => void
}

// ─── Constants ────────────────────────────────────────────────────────────────

const MAX_VISIBLE_TOASTS = 3
const AUTO_DISMISS_MS = 5000

// ─── Helper: create toast message from meeting event ──────────────────────────

/**
 * Creates a toast payload from a meeting event.
 * Returns null for event types that don't produce toasts.
 *
 * Requirements: 5.1, 5.2, 5.3
 */
export function createToastMessage(
  event: MeetingEvent,
): Omit<Toast, 'id' | 'createdAt'> | null {
  switch (event.type) {
    case 'RAISE_HAND': {
      const payload = event.payload as RaiseHandPayload
      return {
        message: `${payload.userName} raised their hand`,
        icon: '✋',
        type: 'info',
      }
    }
    case 'SPEAKING_PERMISSION_GRANTED': {
      const payload = event.payload as SpeakingPermissionPayload
      return {
        message: `${payload.userName} was granted speaking permission`,
        icon: '🎤',
        type: 'success',
      }
    }
    case 'SPEAKING_PERMISSION_REVOKED': {
      return {
        message: 'Speaking permission was revoked',
        icon: '🔇',
        type: 'warning',
      }
    }
    default:
      return null
  }
}

// ─── Store ────────────────────────────────────────────────────────────────────

let idCounter = 0

function generateId(): string {
  idCounter += 1
  return `toast-${Date.now()}-${idCounter}`
}

/**
 * Toast store — manages transient notification toasts for meeting events.
 *
 * Queue behavior: maximum 3 visible toasts at any time. When a new toast is
 * added and the queue is full, the oldest toast is removed first.
 * Auto-dismiss: each toast is automatically removed after 5 seconds.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5
 */
const useToastStore = create<ToastState>((set, get) => ({
  toasts: [],

  addToast: (toast) => {
    const id = generateId()
    const newToast: Toast = {
      ...toast,
      id,
      createdAt: Date.now(),
    }

    set((state) => {
      let toasts = [...state.toasts]

      // Queue behavior: remove oldest if at max capacity
      if (toasts.length >= MAX_VISIBLE_TOASTS) {
        toasts = toasts.slice(1)
      }

      return { toasts: [...toasts, newToast] }
    })

    // Auto-dismiss after 5 seconds
    setTimeout(() => {
      get().removeToast(id)
    }, AUTO_DISMISS_MS)
  },

  removeToast: (id) => {
    set((state) => ({
      toasts: state.toasts.filter((t) => t.id !== id),
    }))
  },

  clearAll: () => {
    set({ toasts: [] })
  },
}))

export default useToastStore
