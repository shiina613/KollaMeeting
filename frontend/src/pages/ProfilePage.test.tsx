import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ProfilePage from './ProfilePage'

const mockGetCurrentUser = vi.fn()
const mockUpdateCurrentUser = vi.fn()
const mockUploadCurrentUserAvatar = vi.fn()

vi.mock('../services/userService', () => ({
  getCurrentUser: () => mockGetCurrentUser(),
  updateCurrentUser: (data: unknown) => mockUpdateCurrentUser(data),
  uploadCurrentUserAvatar: (file: File) => mockUploadCurrentUserAvatar(file),
  changeOwnPassword: vi.fn(),
}))

describe('ProfilePage avatar', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
    URL.createObjectURL = vi.fn(() => 'blob:avatar-preview')
    URL.revokeObjectURL = vi.fn()
    mockGetCurrentUser.mockResolvedValue({
      data: {
        id: 1,
        username: 'tungnq',
        employeeCode: 'tungnq',
        fullName: 'Nguyen Quang Tung',
        email: 'tungnq@kolla.local',
        role: 'USER',
        img: '/uploads/avatars/tungnq.png',
      },
    })
    mockUploadCurrentUserAvatar.mockResolvedValue({
      data: {
        id: 1,
        username: 'tungnq',
        employeeCode: 'tungnq',
        fullName: 'Nguyen Quang Tung',
        email: 'tungnq@kolla.local',
        role: 'USER',
        img: '/api/v1/users/1/avatar',
      },
    })
    mockUpdateCurrentUser.mockImplementation((data) => Promise.resolve({
      data: {
        id: 1,
        username: 'tungnq',
        employeeCode: 'tungnq',
        fullName: 'Nguyen Quang Tung',
        email: 'tungnq@kolla.local',
        role: 'USER',
        ...data,
      },
    }))
  })

  it('uploads a selected avatar image instead of asking users to type a path', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <ProfilePage />
      </MemoryRouter>,
    )

    const avatar = await screen.findByAltText(/Nguyen Quang Tung/i)
    expect(avatar).toHaveAttribute('src', '/uploads/avatars/tungnq.png')
    expect(screen.queryByPlaceholderText('/uploads/avatars/tungnq.png')).not.toBeInTheDocument()

    const avatarInput = screen.getByLabelText(/chon anh dai dien/i)
    const file = new File(['avatar'], 'avatar.png', { type: 'image/png' })
    await user.upload(avatarInput, file)

    expect(avatar).toHaveAttribute('src', 'blob:avatar-preview')

    await user.click(screen.getByRole('button', { name: /L.u h. s./i }))

    await waitFor(() => {
      expect(mockUploadCurrentUserAvatar).toHaveBeenCalledWith(file)
      expect(mockUpdateCurrentUser).toHaveBeenCalledWith(
        expect.objectContaining({ img: '/api/v1/users/1/avatar' }),
      )
    })
  })
})
