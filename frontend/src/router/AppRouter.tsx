import { lazy, Suspense } from 'react'
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
} from 'react-router-dom'
import useAuthStore from '../store/authStore'
import type { UserRole } from '../types/user'

// ─── Lazy-loaded page components ─────────────────────────────────────────────
const LoginPage        = lazy(() => import('../pages/LoginPage'))
const DashboardPage    = lazy(() => import('../pages/DashboardPage'))
const MeetingListPage  = lazy(() => import('../pages/MeetingListPage'))
const MeetingFormPage  = lazy(() => import('../pages/MeetingFormPage'))
const MeetingDetailPage = lazy(() => import('../pages/MeetingDetailPage'))
const MeetingRoomPage  = lazy(() => import('../pages/MeetingRoomPage'))
const AdminPage        = lazy(() => import('../pages/AdminPage'))

// ─── Layout wrapper (Sidebar + Header) ───────────────────────────────────────
// Imported lazily to avoid circular deps; layout components implemented in 14.5
const AppLayout = lazy(() => import('../components/layout/AppLayout'))

// ─── Loading fallback ─────────────────────────────────────────────────────────
const PageLoader = () => (
  <div className="min-h-screen flex items-center justify-center bg-background">
    <div className="flex flex-col items-center gap-3">
      <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
      <span className="text-body-sm text-on-surface-variant">Đang tải...</span>
    </div>
  </div>
)

// ─── ProtectedRoute ───────────────────────────────────────────────────────────
interface ProtectedRouteProps {
  /** Roles allowed to access this route. If undefined, any authenticated user is allowed. */
  allowedRoles?: UserRole[]
}

/**
 * Wraps routes that require authentication (and optionally specific roles).
 * - Not authenticated → redirect to /login
 * - Authenticated but wrong role → redirect to /
 * Requirements: 1.6, 16.7
 */
function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const { isAuthenticated, user } = useAuthStore()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}

// ─── AppRouter ────────────────────────────────────────────────────────────────
/**
 * Application router using React Router v6.
 * All page components are lazy-loaded for code splitting.
 * Requirements: 1.6, 16.7
 */
export default function AppRouter() {
  return (
    <BrowserRouter>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          {/* Public routes */}
          <Route path="/login" element={<LoginPage />} />

          {/* Protected routes — all authenticated users */}
          <Route element={<ProtectedRoute />}>
            <Route element={<AppLayout />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/meetings" element={<MeetingListPage />} />
              <Route path="/meetings/:id" element={<MeetingDetailPage />} />
              <Route path="/meetings/:id/room" element={<MeetingRoomPage />} />
            </Route>
          </Route>

          {/* Protected routes — ADMIN and SECRETARY only */}
          <Route element={<ProtectedRoute allowedRoles={['ADMIN', 'SECRETARY']} />}>
            <Route element={<AppLayout />}>
              <Route path="/meetings/new" element={<MeetingFormPage />} />
              <Route path="/meetings/:id/edit" element={<MeetingFormPage />} />
            </Route>
          </Route>

          {/* Protected routes — ADMIN only */}
          <Route element={<ProtectedRoute allowedRoles={['ADMIN']} />}>
            <Route element={<AppLayout />}>
              <Route path="/admin" element={<AdminPage />} />
            </Route>
          </Route>

          {/* Catch-all → redirect to / */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  )
}
