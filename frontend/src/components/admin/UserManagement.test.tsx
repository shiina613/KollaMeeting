/**
 * Component tests for UserManagement.
 *
 * Tests:
 * 1. Renders role badges correctly (ADMIN/SECRETARY/USER)
 * 2. Password reset flow — dialog opens on button click
 * 3. Password reset flow — calls resetPassword API with correct args
 * 4. Password reset flow — shows success message on success
 * 5. Password reset flow — shows error message on API failure
 *
 * Requirements: 20.4
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import UserManagement from './UserManagement'
import type { MeetingUser } from '../../types/meeting'
import type { PageResponse, ApiResponse } from '../../types/api'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../../services/userService', () => ({
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  deleteUser: vi.fn(),
  resetPassword: vi.fn(),
}))

import {
  listUsers,
  resetPassword,
} from '../../services/userService'

// ─── Test data ────────────────────────────────────────────────────────────────

const makeUser = (
  id: number,
  fullName: string,
  role: 'ADMIN' | 'SECRETARY' | 'USER',
): MeetingUser => ({
  id,
  username: `user${id}`,
  fullName,
  email: `user${id}@example.com`,
  role,
})

const ADMIN_USER = makeUser(1, 'Nguyễn Văn Admin', 'ADMIN')
const SECRETARY_USER = makeUser(2, 'Trần Thị Thư Ký', 'SECRETARY')
const REGULAR_USER = makeUser(3, 'Lê Văn User', 'USER')

function makePageResponse(users: MeetingUser[]): ApiResponse<PageResponse<MeetingUser>> {
  return {
    success: true,
    data: {
      content: users,
      totalElements: users.length,
      totalPages: 1,
      size: 10,
      number: 0,
    },
  }
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(listUsers).mockResolvedValue(makePageResponse([ADMIN_USER, SECRETARY_USER, REGULAR_USER]))
})

// ─── Test 1: Role badge display ───────────────────────────────────────────────

describe('UserManagement — role badge display', () => {
  it('shows ADMIN badge for admin users', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId('role-badge-ADMIN')).toBeInTheDocument()
    })
    expect(screen.getByTestId('role-badge-ADMIN')).toHaveTextContent('Quản trị viên')
  })

  it('shows SECRETARY badge for secretary users', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId('role-badge-SECRETARY')).toBeInTheDocument()
    })
    expect(screen.getByTestId('role-badge-SECRETARY')).toHaveTextContent('Thư ký')
  })

  it('shows USER badge for regular users', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId('role-badge-USER')).toBeInTheDocument()
    })
    expect(screen.getByTestId('role-badge-USER')).toHaveTextContent('Người dùng')
  })

  it('renders all three role badges when all roles are present', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId('user-table')).toBeInTheDocument()
    })

    expect(screen.getByTestId('role-badge-ADMIN')).toBeInTheDocument()
    expect(screen.getByTestId('role-badge-SECRETARY')).toBeInTheDocument()
    expect(screen.getByTestId('role-badge-USER')).toBeInTheDocument()
  })
})

// ─── Test 2: Reset password dialog opens ─────────────────────────────────────

describe('UserManagement — reset password dialog', () => {
  it('opens reset password dialog when reset button is clicked', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`))

    expect(screen.getByTestId('reset-password-dialog')).toBeInTheDocument()
  })

  it('closes reset password dialog when cancel is clicked', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`))
    expect(screen.getByTestId('reset-password-dialog')).toBeInTheDocument()

    // Click cancel
    await userEvent.click(screen.getByText('Hủy'))
    expect(screen.queryByTestId('reset-password-dialog')).not.toBeInTheDocument()
  })

  it('shows the correct user name in the reset password dialog', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${SECRETARY_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${SECRETARY_USER.id}`))

    expect(screen.getByTestId('reset-password-dialog')).toHaveTextContent(SECRETARY_USER.fullName)
  })
})

// ─── Test 3: Reset password API call ─────────────────────────────────────────

describe('UserManagement — reset password API call', () => {
  it('calls resetPassword with correct userId and new password', async () => {
    vi.mocked(resetPassword).mockResolvedValue({ success: true, data: undefined })

    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${REGULAR_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${REGULAR_USER.id}`))

    const input = screen.getByTestId('reset-password-input')
    await userEvent.type(input, 'NewPassword123!')

    await userEvent.click(screen.getByTestId('reset-password-submit'))

    await waitFor(() => {
      expect(vi.mocked(resetPassword)).toHaveBeenCalledWith(REGULAR_USER.id, 'NewPassword123!')
    })
  })

  it('does not call resetPassword when password is empty', async () => {
    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`))

    // Submit without typing a password — HTML required validation prevents submission
    await userEvent.click(screen.getByTestId('reset-password-submit'))

    expect(vi.mocked(resetPassword)).not.toHaveBeenCalled()
  })
})

// ─── Test 4: Reset password success ──────────────────────────────────────────

describe('UserManagement — reset password success', () => {
  it('shows success message after successful password reset', async () => {
    vi.mocked(resetPassword).mockResolvedValue({ success: true, data: undefined })

    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`))
    await userEvent.type(screen.getByTestId('reset-password-input'), 'NewPass123!')
    await userEvent.click(screen.getByTestId('reset-password-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('reset-password-success')).toBeInTheDocument()
    })
    expect(screen.getByTestId('reset-password-success')).toHaveTextContent('thành công')
  })
})

// ─── Test 5: Reset password error ────────────────────────────────────────────

describe('UserManagement — reset password error', () => {
  it('shows error message when resetPassword API fails with server message', async () => {
    vi.mocked(resetPassword).mockRejectedValue({
      response: { data: { message: 'Mật khẩu không đủ mạnh' } },
    })

    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`))
    await userEvent.type(screen.getByTestId('reset-password-input'), 'weak')
    await userEvent.click(screen.getByTestId('reset-password-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('reset-password-error')).toBeInTheDocument()
    })
    expect(screen.getByTestId('reset-password-error')).toHaveTextContent('Mật khẩu không đủ mạnh')
  })

  it('shows fallback error message when API error has no message', async () => {
    vi.mocked(resetPassword).mockRejectedValue(new Error('Network error'))

    render(<UserManagement />)

    await waitFor(() => {
      expect(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`)).toBeInTheDocument()
    })

    await userEvent.click(screen.getByTestId(`reset-password-btn-${ADMIN_USER.id}`))
    await userEvent.type(screen.getByTestId('reset-password-input'), 'SomePassword1!')
    await userEvent.click(screen.getByTestId('reset-password-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('reset-password-error')).toBeInTheDocument()
    })
    expect(screen.getByTestId('reset-password-error')).toHaveTextContent(
      'Không thể đặt lại mật khẩu. Vui lòng thử lại.',
    )
  })
})
