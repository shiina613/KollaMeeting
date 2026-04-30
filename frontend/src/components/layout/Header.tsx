import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import useAuthStore from '../../store/authStore'
import useNotificationStore from '../../store/notificationStore'
import NotificationPanel from './NotificationPanel'

/**
 * Top header with user info, logout button, and notification bell.
 * Requirements: 1.4, 10.5, 10.6
 */
export default function Header() {
  const { user, logout } = useAuthStore()
  const { unreadCount } = useNotificationStore()
  const navigate = useNavigate()
  const [notificationOpen, setNotificationOpen] = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const roleLabel: Record<string, string> = {
    ADMIN:     'Quản trị viên',
    SECRETARY: 'Thư ký',
    USER:      'Người dùng',
  }

  return (
    <>
      <header
        className="fixed top-0 left-64 right-0 h-16 bg-white border-b border-slate-200
                   flex items-center justify-between px-6 z-40"
        role="banner"
      >
        {/* Left: page title placeholder — pages can override via context if needed */}
        <div className="flex items-center gap-2">
          <span className="text-base font-semibold text-on-surface">Kolla Meeting</span>
        </div>

        {/* Right: actions */}
        <div className="flex items-center gap-3">
          {/* Notification bell */}
          <button
            onClick={() => setNotificationOpen((prev) => !prev)}
            className="relative p-2 rounded-lg hover:bg-slate-100 text-slate-600
                       hover:text-slate-900 transition-colors"
            aria-label={`Thông báo${unreadCount > 0 ? ` (${unreadCount} chưa đọc)` : ''}`}
            aria-expanded={notificationOpen}
            aria-haspopup="dialog"
          >
            <span className="material-symbols-outlined text-xl" aria-hidden="true">
              notifications
            </span>
            {unreadCount > 0 && (
              <span
                className="absolute top-1 right-1 min-w-[18px] h-[18px] px-1
                           bg-error text-white text-[10px] font-bold rounded-full
                           flex items-center justify-center leading-none"
                aria-hidden="true"
              >
                {unreadCount > 99 ? '99+' : unreadCount}
              </span>
            )}
          </button>

          {/* User info */}
          {user && (
            <div className="flex items-center gap-2 pl-3 border-l border-slate-200">
              {/* Avatar */}
              <div
                className="w-8 h-8 rounded-full bg-primary flex items-center justify-center
                           text-white text-sm font-semibold flex-shrink-0"
                aria-hidden="true"
              >
                {user.username.charAt(0).toUpperCase()}
              </div>

              {/* Name + role */}
              <div className="hidden sm:flex flex-col">
                <span className="text-sm font-medium text-on-surface leading-tight">
                  {user.username}
                </span>
                <span className="text-xs text-on-surface-variant leading-tight">
                  {roleLabel[user.role] ?? user.role}
                </span>
              </div>

              {/* Logout button */}
              <button
                onClick={handleLogout}
                className="ml-1 p-2 rounded-lg hover:bg-slate-100 text-slate-500
                           hover:text-slate-700 transition-colors"
                aria-label="Đăng xuất"
                title="Đăng xuất"
              >
                <span className="material-symbols-outlined text-xl" aria-hidden="true">
                  logout
                </span>
              </button>
            </div>
          )}
        </div>
      </header>

      {/* Notification panel */}
      <NotificationPanel
        isOpen={notificationOpen}
        onClose={() => setNotificationOpen(false)}
      />
    </>
  )
}
