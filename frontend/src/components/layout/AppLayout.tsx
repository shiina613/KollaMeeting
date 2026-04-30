import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'

/**
 * Main application layout: fixed Sidebar (left) + fixed Header (top) + scrollable content area.
 * Used by all protected routes.
 */
export default function AppLayout() {
  return (
    <div className="min-h-screen bg-background">
      {/* Fixed sidebar */}
      <Sidebar />

      {/* Fixed header — offset by sidebar width (w-64 = 256px) */}
      <Header />

      {/* Main content area — offset by sidebar + header */}
      <main
        className="ml-64 pt-16 min-h-screen"
        id="main-content"
        role="main"
      >
        <div className="p-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
