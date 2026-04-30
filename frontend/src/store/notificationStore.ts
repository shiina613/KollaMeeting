import { create } from 'zustand'
import type { Notification } from '../types/notification'
import api from '../services/api'

// ─── State & actions interface ────────────────────────────────────────────────

interface NotificationState {
  /** Full notification list, newest first */
  notifications: Notification[]
  /** Count of unread notifications */
  unreadCount: number
  /** Whether a fetch/mark operation is in flight */
  isLoading: boolean

  // ── Local mutations (used by WebSocket handler) ──────────────────────────
  /** Replace the entire list (e.g. after fetching from API) */
  setNotifications: (notifications: Notification[]) => void
  /** Prepend a single notification (e.g. received via WebSocket) */
  addNotification: (notification: Notification) => void
  /** Clear all notifications from local state */
  clearNotifications: () => void

  // ── API-backed actions ───────────────────────────────────────────────────
  /** Fetch notification list from backend and populate store */
  fetchNotifications: () => Promise<void>
  /** Mark a single notification as read (local + API) */
  markAsRead: (id: number) => Promise<void>
  /** Mark all notifications as read (local + API) */
  markAllAsRead: () => Promise<void>
}

// ─── Helper ───────────────────────────────────────────────────────────────────

function countUnread(notifications: Notification[]): number {
  return notifications.filter((n) => !n.read).length
}

// ─── Store ────────────────────────────────────────────────────────────────────

/**
 * Notification store — manages the notification list and read state.
 *
 * Local mutations are used by the WebSocket handler to push incoming
 * notifications without a round-trip. API-backed actions sync with the
 * backend for persistence.
 *
 * Requirements: 10.5, 10.6
 */
const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,
  isLoading: false,

  // ── Local mutations ────────────────────────────────────────────────────────

  setNotifications: (notifications) => {
    set({ notifications, unreadCount: countUnread(notifications) })
  },

  addNotification: (notification) => {
    // Deduplicate by id — WebSocket may deliver the same event twice on reconnect
    const existing = get().notifications
    if (existing.some((n) => n.id === notification.id)) return

    const notifications = [notification, ...existing]
    set({ notifications, unreadCount: countUnread(notifications) })
  },

  clearNotifications: () => {
    set({ notifications: [], unreadCount: 0 })
  },

  // ── API-backed actions ─────────────────────────────────────────────────────

  fetchNotifications: async () => {
    set({ isLoading: true })
    try {
      const response = await api.get<Notification[]>('/notifications')
      const notifications = response.data
      set({ notifications, unreadCount: countUnread(notifications), isLoading: false })
    } catch (error) {
      console.error('[notificationStore] Failed to fetch notifications:', error)
      set({ isLoading: false })
    }
  },

  markAsRead: async (id) => {
    // Optimistic local update
    const notifications = get().notifications.map((n) =>
      n.id === id ? { ...n, read: true } : n,
    )
    set({ notifications, unreadCount: countUnread(notifications) })

    try {
      await api.put(`/notifications/${id}/read`)
    } catch (error) {
      console.error('[notificationStore] Failed to mark notification as read:', error)
      // Revert optimistic update on failure
      const reverted = get().notifications.map((n) =>
        n.id === id ? { ...n, read: false } : n,
      )
      set({ notifications: reverted, unreadCount: countUnread(reverted) })
    }
  },

  markAllAsRead: async () => {
    // Optimistic local update
    const notifications = get().notifications.map((n) => ({ ...n, read: true }))
    set({ notifications, unreadCount: 0 })

    try {
      await api.put('/notifications/read-all')
    } catch (error) {
      console.error('[notificationStore] Failed to mark all notifications as read:', error)
      // Revert optimistic update on failure — restore original read states
      // We can't recover the original state here without a snapshot, so re-fetch
      get().fetchNotifications()
    }
  },
}))

export default useNotificationStore
