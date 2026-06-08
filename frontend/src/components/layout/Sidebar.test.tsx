import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import Sidebar from './Sidebar'

vi.mock('../../store/authStore', () => ({
  default: () => ({
    user: { id: 1, role: 'USER', username: 'user1', fullName: 'User 1' },
    logout: vi.fn(),
  }),
}))

describe('Sidebar DOCX demo scope', () => {
  it('hides recordings and labels search as minutes lookup', () => {
    render(
      <MemoryRouter>
        <Sidebar />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link', { name: /Ghi âm/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Tìm kiếm/i })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Tra cứu biên bản/i })).toHaveAttribute('href', '/search')
  })
})
