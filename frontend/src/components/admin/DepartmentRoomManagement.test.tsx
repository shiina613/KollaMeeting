import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DepartmentRoomManagement from './DepartmentRoomManagement'
import {
  createDepartment,
  listDepartments,
  updateDepartment,
} from '../../services/departmentService'
import {
  createRoom,
  listRooms,
  updateRoom,
} from '../../services/roomService'

vi.mock('../../services/departmentService', () => ({
  listDepartments: vi.fn(),
  createDepartment: vi.fn(),
  updateDepartment: vi.fn(),
  deleteDepartment: vi.fn(),
}))

vi.mock('../../services/roomService', () => ({
  listRooms: vi.fn(),
  createRoom: vi.fn(),
  updateRoom: vi.fn(),
  deleteRoom: vi.fn(),
}))

const departments = [
  {
    id: 1,
    departmentCode: 'BGD',
    name: 'Ban Giám Đốc',
    description: 'Không hiển thị ở frontend',
  },
]

const rooms = [
  {
    id: 10,
    roomCode: 'ROOM-MAIN',
    name: 'Phòng họp chính',
    capacity: 25,
    departmentId: 1,
    departmentName: 'Ban Giám Đốc',
  },
]

describe('DepartmentRoomManagement department description field', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listDepartments).mockResolvedValue({ success: true, data: departments })
    vi.mocked(listRooms).mockResolvedValue({ success: true, data: [] })
    vi.mocked(createDepartment).mockResolvedValue({ success: true, data: departments[0] })
    vi.mocked(updateDepartment).mockResolvedValue({ success: true, data: departments[0] })
  })

  it('does not render the department description column or value', async () => {
    render(<DepartmentRoomManagement view="departments" />)

    await waitFor(() => {
      expect(screen.getByText('BGD')).toBeInTheDocument()
    })

    const table = screen.getByRole('table')
    expect(within(table).queryByRole('columnheader', { name: 'Mô tả' })).not.toBeInTheDocument()
    expect(within(table).queryByText('Không hiển thị ở frontend')).not.toBeInTheDocument()
  })

  it('does not show or submit a description field when creating a department', async () => {
    const user = userEvent.setup()
    render(<DepartmentRoomManagement view="departments" />)

    await user.click(await screen.findByRole('button', { name: /thêm phòng ban/i }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByLabelText('Mô tả')).not.toBeInTheDocument()

    await user.type(within(dialog).getByLabelText(/mã phòng ban/i), 'NS')
    await user.type(within(dialog).getByLabelText(/tên phòng ban/i), 'Nhân sự')
    await user.click(within(dialog).getByRole('button', { name: /^thêm phòng ban$/i }))

    await waitFor(() => {
      expect(createDepartment).toHaveBeenCalledWith({
        departmentCode: 'NS',
        name: 'Nhân sự',
      })
    })
  })

  it('does not show or submit a description field when editing a department', async () => {
    const user = userEvent.setup()
    render(<DepartmentRoomManagement view="departments" />)

    await user.click(await screen.findByRole('button', { name: /chỉnh sửa ban giám đốc/i }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByLabelText('Mô tả')).not.toBeInTheDocument()

    await user.clear(within(dialog).getByLabelText(/tên phòng ban/i))
    await user.type(within(dialog).getByLabelText(/tên phòng ban/i), 'Ban Lãnh Đạo')
    await user.click(within(dialog).getByRole('button', { name: /lưu thay đổi/i }))

    await waitFor(() => {
      expect(updateDepartment).toHaveBeenCalledWith(1, {
        departmentCode: 'BGD',
        name: 'Ban Lãnh Đạo',
      })
    })
  })
})

describe('DepartmentRoomManagement room department and capacity fields', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listDepartments).mockResolvedValue({ success: true, data: departments })
    vi.mocked(listRooms).mockResolvedValue({ success: true, data: rooms })
    vi.mocked(createRoom).mockResolvedValue({ success: true, data: rooms[0] })
    vi.mocked(updateRoom).mockResolvedValue({ success: true, data: rooms[0] })
  })

  it('does not render room department or capacity columns and values', async () => {
    render(<DepartmentRoomManagement view="rooms" />)

    await waitFor(() => {
      expect(screen.getByText('ROOM-MAIN')).toBeInTheDocument()
    })

    const table = screen.getByRole('table')
    expect(within(table).queryByRole('columnheader', { name: 'Phòng ban' })).not.toBeInTheDocument()
    expect(within(table).queryByRole('columnheader', { name: 'Sức chứa' })).not.toBeInTheDocument()
    expect(within(table).queryByText('Ban Giám Đốc')).not.toBeInTheDocument()
    expect(within(table).queryByText('25 người')).not.toBeInTheDocument()
  })

  it('does not show room department or capacity fields when creating a room', async () => {
    const user = userEvent.setup()
    render(<DepartmentRoomManagement view="rooms" />)

    await user.click(await screen.findByRole('button', { name: /thêm phòng họp/i }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByLabelText('Phòng ban')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Sức chứa')).not.toBeInTheDocument()

    await user.type(within(dialog).getByLabelText(/mã phòng họp/i), 'ROOM-NEW')
    await user.type(within(dialog).getByLabelText(/tên phòng họp/i), 'Phòng họp mới')
    await user.click(within(dialog).getByRole('button', { name: /^thêm phòng họp$/i }))

    await waitFor(() => {
      expect(createRoom).toHaveBeenCalledWith({
        roomCode: 'ROOM-NEW',
        name: 'Phòng họp mới',
        roomName: 'Phòng họp mới',
        departmentId: 1,
      })
    })
  })

  it('does not show or submit room department or capacity fields when editing a room', async () => {
    const user = userEvent.setup()
    render(<DepartmentRoomManagement view="rooms" />)

    await user.click(await screen.findByRole('button', { name: /chỉnh sửa phòng họp chính/i }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByLabelText('Phòng ban')).not.toBeInTheDocument()
    expect(within(dialog).queryByLabelText('Sức chứa')).not.toBeInTheDocument()

    await user.clear(within(dialog).getByLabelText(/tên phòng họp/i))
    await user.type(within(dialog).getByLabelText(/tên phòng họp/i), 'Phòng họp trung tâm')
    await user.click(within(dialog).getByRole('button', { name: /lưu thay đổi/i }))

    await waitFor(() => {
      expect(updateRoom).toHaveBeenCalledWith(10, {
        roomCode: 'ROOM-MAIN',
        name: 'Phòng họp trung tâm',
        roomName: 'Phòng họp trung tâm',
        departmentId: 1,
      })
    })
  })
})
