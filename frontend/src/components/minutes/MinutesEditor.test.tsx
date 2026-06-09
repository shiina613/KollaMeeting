/**
 * Component tests for MinutesEditor.
 *
 * Tests:
 * 1. Renders correctly with initial content
 * 2. Renders nothing when user is not SECRETARY or ADMIN
 * 3. Submits with correct contentHtml when form is submitted
 * 4. Shows loading state during submission
 * 5. Shows success message after successful submission
 * 6. Shows error message when API fails
 * 7. Calls onSuccess callback after successful submission
 * 8. Disables submit button when content is empty
 *
 * Requirements: 20.4
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MinutesEditor from './MinutesEditor'

// â”€â”€â”€ Mocks â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

vi.mock('../../services/minutesService', () => ({
  editMinutes: vi.fn(),
}))

vi.mock('../../store/authStore', () => {
  const mockUser = {
    id: 1,
    username: 'secretary1',
    email: 'secretary@example.com',
    role: 'SECRETARY' as const,
  }
  const useAuthStore = vi.fn(() => ({ user: mockUser }))
  useAuthStore.getState = vi.fn(() => ({ user: mockUser, token: 'test-token' }))
  return { default: useAuthStore }
})
import { editMinutes } from '../../services/minutesService'
import useAuthStore from '../../store/authStore'

// â”€â”€â”€ Helpers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

function renderEditor(props: {
  meetingId?: number
  initialContent?: string
  initialEntries?: import('../../types/minutes').MinutesContentEntry[]
  initialConclusion?: string
  onSuccess?: () => void
} = {}) {
  return render(
    <MinutesEditor
      meetingId={props.meetingId ?? 1}
      initialContent={props.initialContent}
      initialEntries={props.initialEntries}
      initialConclusion={props.initialConclusion}
      onSuccess={props.onSuccess}
    />,
  )
}

// â”€â”€â”€ Setup â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

beforeEach(() => {
  vi.clearAllMocks()
  // Default: SECRETARY user
  vi.mocked(useAuthStore).mockReturnValue({
    user: { id: 1, username: 'secretary1', email: 'secretary@example.com', role: 'SECRETARY' },
  } as ReturnType<typeof useAuthStore>)
})

// â”€â”€â”€ Test 1: Renders with initial content â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” renders correctly', () => {
  it('renders the editor form for SECRETARY role', () => {
    renderEditor()

    expect(screen.getByTestId('minutes-editor')).toBeInTheDocument()
    expect(screen.getByTestId('minutes-entry-text-0')).toBeInTheDocument()
    expect(screen.getByTestId('minutes-editor-submit')).toBeInTheDocument()
  })

  it('renders with initial content in the textarea', () => {
    const initialContent = '<h1>BiÃªn báº£n</h1><p>Ná»™i dung cuá»™c há»p</p>'
    renderEditor({ initialContent })

    expect(screen.getByTestId('minutes-entry-text-0')).toHaveValue(initialContent)
  })

  it('renders with empty textarea when no initial content provided', () => {
    renderEditor()

    expect(screen.getByTestId('minutes-entry-text-0')).toHaveValue('')
  })

  it('renders for ADMIN role as well', () => {
    vi.mocked(useAuthStore).mockReturnValue({
      user: { id: 2, username: 'admin1', email: 'admin@example.com', role: 'ADMIN' },
    } as ReturnType<typeof useAuthStore>)

    renderEditor()

    expect(screen.getByTestId('minutes-editor')).toBeInTheDocument()
  })
})

// â”€â”€â”€ Test 2: Role-based visibility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” role-based visibility', () => {
  it('renders nothing for USER role', () => {
    vi.mocked(useAuthStore).mockReturnValue({
      user: { id: 3, username: 'user1', email: 'user@example.com', role: 'USER' },
    } as ReturnType<typeof useAuthStore>)

    const { container } = renderEditor()

    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing when user is null', () => {
    vi.mocked(useAuthStore).mockReturnValue({
      user: null,
    } as ReturnType<typeof useAuthStore>)

    const { container } = renderEditor()

    expect(container).toBeEmptyDOMElement()
  })
})

// â”€â”€â”€ Test 3: Submit calls API with correct contentHtml â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” submit behavior', () => {
  it('submits structured entries and conclusion', async () => {
    vi.mocked(editMinutes).mockResolvedValue({
      data: {} as import('../../types/minutes').Minutes,
      success: true,
    })

    renderEditor({
      meetingId: 42,
      initialEntries: [{ speakerName: 'Nguyen Van A', roleLabel: 'Chu tri', timeLabel: '09:01', text: 'Noi dung cu' }],
      initialConclusion: '',
    })

    const entry = screen.getByTestId('minutes-entry-text-0')
    await userEvent.clear(entry)
    await userEvent.type(entry, 'Edited speech')
    await userEvent.type(screen.getByTestId('minutes-conclusion-input'), 'Edited conclusion')
    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(vi.mocked(editMinutes)).toHaveBeenCalledWith(42, {
        contentEntries: [{ speakerName: 'Nguyen Van A', roleLabel: 'Chu tri', timeLabel: '09:01', text: 'Edited speech' }],
        conclusion: 'Edited conclusion',
      })
    })
  })

  it('calls editMinutes with correct meetingId and contentHtml on submit', async () => {
    vi.mocked(editMinutes).mockResolvedValue({
      data: {} as import('../../types/minutes').Minutes,
      success: true,
    })

    renderEditor({ meetingId: 42, initialContent: '<p>Ná»™i dung</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(vi.mocked(editMinutes)).toHaveBeenCalledWith(42, {
        contentEntries: [{ speakerName: '', roleLabel: '', timeLabel: '', text: '<p>Ná»™i dung</p>' }],
        conclusion: '',
      })
    })
  })

  it('calls editMinutes with updated content after user edits', async () => {
    vi.mocked(editMinutes).mockResolvedValue({
      data: {} as import('../../types/minutes').Minutes,
      success: true,
    })

    renderEditor({ meetingId: 5 })

    const textarea = screen.getByTestId('minutes-entry-text-0')
    await userEvent.type(textarea, '<h1>TiÃªu Ä‘á»</h1>')

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(vi.mocked(editMinutes)).toHaveBeenCalledWith(5, {
        contentEntries: [{ speakerName: '', roleLabel: '', timeLabel: '', text: '<h1>TiÃªu Ä‘á»</h1>' }],
        conclusion: '',
      })
    })
  })
})

// â”€â”€â”€ Test 4: Loading state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” loading state', () => {
  it('shows loading indicator during submission', async () => {
    // Never resolves â€” keeps loading state
    vi.mocked(editMinutes).mockReturnValue(new Promise(() => {}))

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    expect(screen.getByTestId('minutes-editor-submit')).toBeDisabled()
    expect(screen.getByTestId('minutes-editor-submit')).toHaveTextContent('Đang lưu...')
  })

  it('disables textarea during submission', async () => {
    vi.mocked(editMinutes).mockReturnValue(new Promise(() => {}))

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    expect(screen.getByTestId('minutes-entry-text-0')).toBeDisabled()
  })
})

// â”€â”€â”€ Test 5: Success feedback â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” success feedback', () => {
  it('shows success message after successful submission', async () => {
    vi.mocked(editMinutes).mockResolvedValue({
      data: {} as import('../../types/minutes').Minutes,
      success: true,
    })

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-success')).toBeInTheDocument()
    })
    expect(screen.getByTestId('minutes-editor-success')).toHaveTextContent('Biên bản đã được lưu thành công.')
  })

  it('calls onSuccess callback after successful submission', async () => {
    vi.mocked(editMinutes).mockResolvedValue({
      data: {} as import('../../types/minutes').Minutes,
      success: true,
    })
    const onSuccess = vi.fn()

    renderEditor({ initialContent: '<p>Content</p>', onSuccess })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledOnce()
    })
  })
})

// â”€â”€â”€ Test 6: Error handling â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” error handling', () => {
  it('shows error message when API fails with a message', async () => {
    vi.mocked(editMinutes).mockRejectedValue({
      response: { data: { message: 'Báº¡n khÃ´ng cÃ³ quyá»n chá»‰nh sá»­a biÃªn báº£n' } },
    })

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-error')).toBeInTheDocument()
    })
    expect(screen.getByTestId('minutes-editor-error')).toHaveTextContent('Báº¡n khÃ´ng cÃ³ quyá»n chá»‰nh sá»­a biÃªn báº£n')
  })

  it('shows fallback error message when API error has no message', async () => {
    vi.mocked(editMinutes).mockRejectedValue(new Error('Network error'))

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
    expect(screen.getByTestId('minutes-editor-error')).toHaveTextContent('Không thể lưu biên bản. Vui lòng thử lại.')
    })
  })

  it('does not call onSuccess when API fails', async () => {
    vi.mocked(editMinutes).mockRejectedValue(new Error('fail'))
    const onSuccess = vi.fn()

    renderEditor({ initialContent: '<p>Content</p>', onSuccess })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-error')).toBeInTheDocument()
    })

    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('clears error message on next successful submission', async () => {
    vi.mocked(editMinutes)
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce({
        data: {} as import('../../types/minutes').Minutes,
        success: true,
      })

    renderEditor({ initialContent: '<p>Content</p>' })

    // First submit â€” fails
    await userEvent.click(screen.getByTestId('minutes-editor-submit'))
    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-error')).toBeInTheDocument()
    })

    // Second submit â€” succeeds
    await userEvent.click(screen.getByTestId('minutes-editor-submit'))
    await waitFor(() => {
      expect(screen.queryByTestId('minutes-editor-error')).not.toBeInTheDocument()
    })
  })
})

// â”€â”€â”€ Test 7: Submit button disabled when content is empty â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” submit button state', () => {
  it('disables submit button when textarea is empty', () => {
    renderEditor({ initialContent: '' })

    expect(screen.getByTestId('minutes-editor-submit')).toBeDisabled()
  })

  it('enables submit button when textarea has content', () => {
    renderEditor({ initialContent: '<p>Content</p>' })

    expect(screen.getByTestId('minutes-editor-submit')).not.toBeDisabled()
  })

  it('disables submit button when content is only whitespace', async () => {
    renderEditor({ initialContent: '' })

    const textarea = screen.getByTestId('minutes-entry-text-0')
    await userEvent.type(textarea, '   ')

    expect(screen.getByTestId('minutes-editor-submit')).toBeDisabled()
  })
})

// â”€â”€â”€ Test 8: Accessibility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor â€” accessibility', () => {
  it('has correct aria-label on the form', () => {
    renderEditor()

    expect(screen.getByTestId('minutes-editor')).toHaveAttribute('aria-label', 'Chỉnh sửa biên bản cuộc họp')
  })

  it('has correct aria-label on the textarea', () => {
    renderEditor()

    expect(screen.getByTestId('minutes-entry-text-0')).toHaveAttribute('aria-label', 'Nội dung phát biểu 1')
  })

  it('success message has role="status"', async () => {
    vi.mocked(editMinutes).mockResolvedValue({
      data: {} as import('../../types/minutes').Minutes,
      success: true,
    })

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-success')).toHaveAttribute('role', 'status')
    })
  })

  it('error message has role="alert"', async () => {
    vi.mocked(editMinutes).mockRejectedValue(new Error('fail'))

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-error')).toHaveAttribute('role', 'alert')
    })
  })
})
