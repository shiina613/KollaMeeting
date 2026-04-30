import { useEffect, useRef } from 'react'
import useNotificationStore from '../../store/notificationStore'
import type { Notification } from '../../types/notification'

interface NotificationPanelProps {
  isOpen: boolean
  onClose: () => void
}

/**
 * Slide-out notification panel.
 * Shows notification list with mark-as-read functionality.
 * Requirements: 10.5, 10.6
 */
export default function NotificationPanel({ isOpen, onClose }: NotificationPanelProps) {
  const { notifications, markAsRead, markAllAsRead } = useNotificationStore()
  const panelRef = useRef<HTMLDivElement>(null)

  // Close on outside click
  useEffect(() => {
    if (!isOpen) return

    const handleClickOutside = (e: MouseEvent) => {
      if (panelRef.current && !panelRef.current.contains(e.target as Node)) {
        onClose()
      }
    }

    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [isOpen, onClose])

  // Close on Escape key
  useEffect(() => {
    if (!isOpen) return

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [isOpen, onClose])

  if (!isOpen) return null

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40 bg-black/10"
        aria-hidden="true"
        onClick={onClose}
      />

      {/* Panel */}
      <div
        ref={panelRef}
        role="dialog"
        aria-label="Thông báo"
        aria-modal="true"
        className="fixed right-0 top-0 h-full w-80 bg-white shadow-xl z-50
                   flex flex-col border-l border-slate-200
                   animate-in slide-in-from-right duration-200"
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-4 border-b border-slate-200">
          <h2 className="text-base font-semibold text-on-surface">Thông báo</h2>
          <div className="flex items-center gap-2">
            {notifications.some((n) => !n.read) && (
              <button
                onClick={markAllAsRead}
                className="text-xs text-primary hover:underline"
                aria-label="Đánh dấu tất cả đã đọc"
              >
                Đọc tất cả
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1 rounded hover:bg-slate-100 text-slate-500 hover:text-slate-700
                         transition-colors"
              aria-label="Đóng thông báo"
            >
              <span className="material-symbols-outlined text-xl" aria-hidden="true">
                close
              </span>
            </button>
          </div>
        </div>

        {/* Notification list */}
        <div className="flex-1 overflow-y-auto">
          {notifications.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-full gap-3 text-on-surface-variant">
              <span className="material-symbols-outlined text-4xl" aria-hidden="true">
                notifications_none
              </span>
              <p className="text-body-sm">Không có thông báo nào</p>
            </div>
          ) : (
            <ul role="list" className="divide-y divide-slate-100">
              {notifications.map((notification) => (
                <NotificationItem
                  key={notification.id}
                  notification={notification}
                  onMarkRead={markAsRead}
                />
              ))}
            </ul>
          )}
        </div>
      </div>
    </>
  )
}

// ─── NotificationItem ─────────────────────────────────────────────────────────

interface NotificationItemProps {
  notification: Notification
  onMarkRead: (id: number) => void
}

function NotificationItem({ notification, onMarkRead }: NotificationItemProps) {
  const handleClick = () => {
    if (!notification.read) {
      onMarkRead(notification.id)
    }
  }

  return (
    <li
      className={`px-4 py-3 cursor-pointer transition-colors hover:bg-slate-50 ${
        !notification.read ? 'bg-blue-50/50' : ''
      }`}
      onClick={handleClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && handleClick()}
      aria-label={`${notification.title}${!notification.read ? ' (chưa đọc)' : ''}`}
    >
      <div className="flex items-start gap-3">
        {/* Unread indicator */}
        <div className="mt-1.5 flex-shrink-0">
          {!notification.read ? (
            <div
              className="w-2 h-2 rounded-full bg-primary"
              aria-label="Chưa đọc"
            />
          ) : (
            <div className="w-2 h-2" />
          )}
        </div>

        <div className="flex-1 min-w-0">
          <p className={`text-sm font-medium text-on-surface truncate ${
            !notification.read ? 'font-semibold' : ''
          }`}>
            {notification.title}
          </p>
          <p className="text-xs text-on-surface-variant mt-0.5 line-clamp-2">
            {notification.message}
          </p>
          <p className="text-xs text-outline mt-1">
            {formatNotificationTime(notification.createdAt)}
          </p>
        </div>
      </div>
    </li>
  )
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatNotificationTime(isoString: string): string {
  try {
    const date = new Date(isoString)
    const now = new Date()
    const diffMs = now.getTime() - date.getTime()
    const diffMins = Math.floor(diffMs / 60_000)

    if (diffMins < 1) return 'Vừa xong'
    if (diffMins < 60) return `${diffMins} phút trước`

    const diffHours = Math.floor(diffMins / 60)
    if (diffHours < 24) return `${diffHours} giờ trước`

    const diffDays = Math.floor(diffHours / 24)
    if (diffDays < 7) return `${diffDays} ngày trước`

    // Format as date in UTC+7
    return date.toLocaleDateString('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    })
  } catch {
    return isoString
  }
}
