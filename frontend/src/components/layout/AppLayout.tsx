import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Header from './Header'

export default function AppLayout() {
  const [drawerOpen, setDrawerOpen] = useState(false)

  const closeDrawer = () => setDrawerOpen(false)

  return (
    <div className='min-h-screen bg-background'>
      <div className='hidden lg:block'>
        <Sidebar />
      </div>

      {drawerOpen && (
        <div className='fixed inset-0 z-50 lg:hidden' data-testid='mobile-sidebar-drawer'>
          <button
            type='button'
            className='absolute inset-0 w-full h-full bg-black/40'
            aria-label='Đóng menu điều hướng'
            data-testid='mobile-sidebar-backdrop'
            onClick={closeDrawer}
          />
          <div className='relative h-full w-64 bg-white shadow-xl'>
            <Sidebar onNavigate={closeDrawer} />
          </div>
        </div>
      )}

      <Header onMenuClick={() => setDrawerOpen(true)} />

      <main className='pt-16 min-h-screen lg:ml-64' id='main-content' role='main'>
        <div className='p-4 sm:p-6'>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
