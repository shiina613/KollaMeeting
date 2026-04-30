import { NavLink, useNavigate } from 'react-router-dom'
import useAuthStore from '../../store/authStore'
import type { UserRole } from '../../types/user'

interface NavItem {
  label: string
  icon: string
  path: string
  roles?: UserRole[]  // undefined = all roles
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Dashboard',   icon: 'dashboard',             path: '/' },
  { label: 'Cuộc họp',   icon: 'video_chat',             path: '/meetings' },
  { label: 'Tìm kiếm',   icon: 'search',                 path: '/search' },
]

const ADMIN_NAV_ITEMS: NavItem[] = [
  { label: 'Quản trị',   icon: 'admin_panel_settings',   path: '/admin', roles: ['ADMIN'] },
]

const BOTTOM_NAV_ITEMS: NavItem[] = [
  { label: 'Hồ sơ',      icon: 'person',                 path: '/profile' },
]

/**
 * Sidebar navigation component.
 * Pixel-perfect implementation based on Stitch design spec.
 * Requirements: 1.4
 */
export default function Sidebar() {
  const { user, logout } = useAuthStore()
  const navigate = useNavigate()

  const handleLogout = async () => {
    logout()
    navigate('/login', { replace: true })
  }

  const visibleAdminItems = ADMIN_NAV_ITEMS.filter(
    (item) => !item.roles || (user && item.roles.includes(user.role)),
  )

  return (
    <nav
      className="bg-white font-inter text-sm font-medium fixed left-0 top-0 h-full w-64
                 border-r border-slate-200 flex flex-col py-6 z-50"
      aria-label="Điều hướng chính"
    >
      {/* Logo section */}
      <div className="px-6 mb-8 flex flex-col gap-1">
        <h1 className="text-lg font-black text-blue-600 uppercase tracking-wider">
          Kolla
        </h1>
        <span className="text-slate-500 text-xs uppercase tracking-widest font-semibold">
          Meeting
        </span>
      </div>

      {/* Main nav items */}
      <div className="flex-1 flex flex-col gap-1 w-full">
        {NAV_ITEMS.map((item) => (
          <SidebarNavLink key={item.path} item={item} />
        ))}

        {/* Admin section — only visible to ADMIN role */}
        {visibleAdminItems.length > 0 && (
          <>
            <div className="mx-4 my-2 border-t border-slate-200" />
            {visibleAdminItems.map((item) => (
              <SidebarNavLink key={item.path} item={item} />
            ))}
          </>
        )}
      </div>

      {/* Bottom section */}
      <div className="mt-auto flex flex-col gap-1 w-full pt-4 border-t border-slate-200">
        {BOTTOM_NAV_ITEMS.map((item) => (
          <SidebarNavLink key={item.path} item={item} />
        ))}

        {/* Logout button */}
        <button
          onClick={handleLogout}
          className="text-slate-600 flex items-center gap-3 px-4 py-3
                     hover:bg-slate-50 hover:text-slate-900 transition-colors duration-150
                     w-full text-left"
          aria-label="Đăng xuất"
        >
          <span className="material-symbols-outlined" aria-hidden="true">
            logout
          </span>
          <span>Đăng xuất</span>
        </button>
      </div>
    </nav>
  )
}

// ─── SidebarNavLink ───────────────────────────────────────────────────────────

interface SidebarNavLinkProps {
  item: NavItem
}

function SidebarNavLink({ item }: SidebarNavLinkProps) {
  return (
    <NavLink
      to={item.path}
      end={item.path === '/'}
      className={({ isActive }) =>
        isActive
          ? 'bg-blue-50 text-blue-600 border-r-4 border-blue-600 rounded-none ' +
            'flex items-center gap-3 px-4 py-3 transition-colors duration-150'
          : 'text-slate-600 flex items-center gap-3 px-4 py-3 ' +
            'hover:bg-slate-50 hover:text-slate-900 transition-colors duration-150'
      }
      aria-label={item.label}
    >
      {({ isActive }) => (
        <>
          <span
            className="material-symbols-outlined"
            style={isActive ? { fontVariationSettings: "'FILL' 1" } : undefined}
            aria-hidden="true"
          >
            {item.icon}
          </span>
          <span>{item.label}</span>
        </>
      )}
    </NavLink>
  )
}
