import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'

vi.mock('../components/admin/UserManagement', () => ({
  default: () => <div>Quản lý người dùng</div>,
}))

vi.mock('../components/admin/DepartmentRoomManagement', () => ({
  default: () => <div>Phòng ban & Phòng họp</div>,
}))

describe('AdminPage DOCX scope', () => {
  it('does not show the storage tab in the submitted frontend', () => {
    render(<AdminPage />)

    expect(screen.queryByRole('tab', { name: /Lưu trữ/i })).not.toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Quản lý người dùng/i })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /Phòng ban & Phòng họp/i })).toBeInTheDocument()
  })
})
