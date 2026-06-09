/**
 * AdminPage — admin panel with tabs for user management and departments/rooms.
 * Protected: ADMIN only.
 * Requirements: 12.4
 */

import { useState } from 'react'
import UserManagement from '../components/admin/UserManagement'
import DepartmentRoomManagement from '../components/admin/DepartmentRoomManagement'

// ─── Tab definitions ──────────────────────────────────────────────────────────

type TabId = 'users' | 'departments' | 'rooms'

interface Tab {
  id: TabId
  label: string
  icon: string
}

const TABS: Tab[] = [
  { id: 'users', label: 'Quản lý người dùng', icon: 'group' },
  { id: 'departments', label: 'Phòng ban', icon: 'corporate_fare' },
  { id: 'rooms', label: 'Phòng họp', icon: 'meeting_room' },
]

// ─── AdminPage ────────────────────────────────────────────────────────────────

export default function AdminPage() {
  const [activeTab, setActiveTab] = useState<TabId>('users')

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-h3 font-semibold text-on-surface">Quản trị hệ thống</h1>
        <p className="text-body-sm text-on-surface-variant mt-1">
          Quản lý người dùng, phòng ban và phòng họp
        </p>
      </div>

      {/* Tabs */}
      <div
        className="flex gap-1 border-b border-outline-variant"
        role="tablist"
        aria-label="Quản trị hệ thống"
        data-testid="admin-tabs"
      >
        {TABS.map((tab) => (
          <button
            key={tab.id}
            role="tab"
            aria-selected={activeTab === tab.id}
            aria-controls={`tab-panel-${tab.id}`}
            id={`tab-${tab.id}`}
            onClick={() => setActiveTab(tab.id)}
            data-testid={`tab-${tab.id}`}
            className={`inline-flex items-center gap-2 px-4 py-3 text-button font-medium border-b-2 transition-colors
              ${activeTab === tab.id
                ? 'border-primary text-primary'
                : 'border-transparent text-on-surface-variant hover:text-on-surface hover:border-outline-variant'
              }`}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
              {tab.icon}
            </span>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab panels */}
      <div
        id="tab-panel-users"
        role="tabpanel"
        aria-labelledby="tab-users"
        hidden={activeTab !== 'users'}
        data-testid="tab-panel-users"
      >
        {activeTab === 'users' && <UserManagement />}
      </div>

      <div
        id="tab-panel-departments"
        role="tabpanel"
        aria-labelledby="tab-departments"
        hidden={activeTab !== 'departments'}
        data-testid="tab-panel-departments"
      >
        {activeTab === 'departments' && <DepartmentRoomManagement view="departments" />}
      </div>

      <div
        id="tab-panel-rooms"
        role="tabpanel"
        aria-labelledby="tab-rooms"
        hidden={activeTab !== 'rooms'}
        data-testid="tab-panel-rooms"
      >
        {activeTab === 'rooms' && <DepartmentRoomManagement view="rooms" />}
      </div>
    </div>
  )
}
