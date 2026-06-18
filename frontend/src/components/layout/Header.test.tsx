import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import Header from './Header'

vi.mock('../../store/authStore', () => ({
  default: () => ({
    user: {
      id: 1,
      username: 'admin',
      fullName: 'System Administrator',
      email: 'admin@kolla.local',
      role: 'ADMIN',
      img: '/api/v1/users/1/avatar',
    },
    logout: vi.fn(),
  }),
}))

vi.mock('../../store/notificationStore', () => ({
  default: () => ({
    unreadCount: 0,
  }),
}))

describe('Header avatar', () => {
  it('shows the saved user avatar in the top-right account area', () => {
    render(
      <MemoryRouter>
        <Header />
      </MemoryRouter>,
    )

    expect(screen.getByRole('img', { name: /System Administrator avatar/i }))
      .toHaveAttribute('src', 'http://localhost:8080/api/v1/users/1/avatar')
  })
})
