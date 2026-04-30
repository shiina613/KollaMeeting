import { create } from 'zustand'
import type { Notification } from '../types/notification'

interface NotificationState {
  notifications: Notification[]
  unreadCount: number

  // Actions
  setNotifications: (notifications: Notification[]) => void
  addNotification: (notification: Notification) => void
  markAsRead: (id: number) => void
  markAllAsRead: () => void
  clearNotifications: () => void
}

const useNotificationStore = create<NotificationState>((set, get) => ({
  notifications: [],
  unreadCount: 0,

  setNotifications: (notifications) => {
    set({
      notifications,
      unreadCount: notifications.filter((n) => !n.read).length,
    })
  },

  addNotification: (notification) => {
    const notifications = [notification, ...get().notifications]
    set({
      notifications,
      unreadCount: notifications.filter((n) => !n.read).length,
    })
  },

  markAsRead: (id) => {
    const notifications = get().notifications.map((n) =>
      n.id === id ? { ...n, read: true } : n,
    )
    set({
      notifications,
      unreadCount: notifications.filter((n) => !n.read).length,
    })
  },

  markAllAsRead: () => {
    const notifications = get().notifications.map((n) => ({ ...n, read: true }))
    set({ notifications, unreadCount: 0 })
  },

  clearNotifications: () => {
    set({ notifications: [], unreadCount: 0 })
  },
}))

export default useNotificationStore
