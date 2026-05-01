/**
 * AdminPage — admin panel with tabs for user management, storage, and departments/rooms.
 * Protected: ADMIN only.
 * Requirements: 12.4
 */

import { useState } from 'react'
import UserManagement from '../components/admin/UserManagement'
import StorageDashboard from '../components/admin/StorageDashboard'

// ─── Tab definitions ──────────────────────────────────────────────────────────

type TabId = 'users' | 'storage' | 'departments'

interface Tab {
  id: TabId
  label: string
  icon: string
}

const TABS: Tab[] = [
  { id: 'users', label: 'Quản lý người dùng', icon: 'group' },
  { id: 'storage', label: 'Lưu trữ', icon: 'storage' },
  { id: 'departments', label: 'Phòng ban & Phòng họp', icon: 'corporate_fare' },
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
          Quản lý người dùng, lưu trữ và cơ sở hạ tầng
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
        id="tab-panel-storage"
        role="tabpanel"
        aria-labelledby="tab-storage"
        hidden={activeTab !== 'storage'}
        data-testid="tab-panel-storage"
      >
        {activeTab === 'storage' && <StorageDashboard />}
      </div>

      <div
        id="tab-panel-departments"
        role="tabpanel"
        aria-labelledby="tab-departments"
        hidden={activeTab !== 'departments'}
        data-testid="tab-panel-departments"
      >
        {activeTab === 'departments' && (
          <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant">
            <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">
              corporate_fare
            </span>
            <p className="text-body-md font-medium text-on-surface mb-1">
              Quản lý Phòng ban & Phòng họp
            </p>
            <p className="text-body-sm text-on-surface-variant">
              Tính năng này sẽ được triển khai trong phiên bản tiếp theo.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
