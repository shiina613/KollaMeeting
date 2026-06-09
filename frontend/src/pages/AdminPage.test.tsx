import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import AdminPage from './AdminPage'

vi.mock('../components/admin/UserManagement', () => ({
  default: () => <div>user-management</div>,
}))

vi.mock('../components/admin/DepartmentRoomManagement', () => ({
  default: ({ view }: { view: string }) => <div>department-room-view:{view}</div>,
}))

describe('AdminPage DOCX scope', () => {
  it('shows departments and rooms as separate top-level tabs without storage', () => {
    render(<AdminPage />)

    expect(screen.queryByTestId('tab-storage')).not.toBeInTheDocument()
    expect(screen.getByTestId('tab-users')).toBeInTheDocument()
    expect(screen.getByTestId('tab-departments')).toBeInTheDocument()
    expect(screen.getByTestId('tab-rooms')).toBeInTheDocument()
  })

  it('opens departments and rooms as separate top-level tabs', async () => {
    const user = userEvent.setup()
    render(<AdminPage />)

    await user.click(screen.getByTestId('tab-departments'))
    expect(screen.getByText('department-room-view:departments')).toBeInTheDocument()

    await user.click(screen.getByTestId('tab-rooms'))
    expect(screen.getByText('department-room-view:rooms')).toBeInTheDocument()
  })
})
