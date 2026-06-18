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
  readonlySummary?: {
    meetingTitle: string
    endedAt?: string
    hostName?: string
    secretaryName?: string
  }
  onCancel?: () => void
  onSuccess?: () => void
} = {}) {
  return render(
    <MinutesEditor
      meetingId={props.meetingId ?? 1}
      initialContent={props.initialContent}
      initialEntries={props.initialEntries}
      initialConclusion={props.initialConclusion}
      readonlySummary={props.readonlySummary}
      onCancel={props.onCancel}
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

describe('MinutesEditor - renders correctly', () => {
  it('renders the editor form for SECRETARY role', () => {
    renderEditor()

    expect(screen.getByTestId('minutes-editor')).toBeInTheDocument()
    expect(screen.getByTestId('minutes-entry-text-0')).toBeInTheDocument()
    expect(screen.getByTestId('minutes-editor-submit')).toBeInTheDocument()
  })

  it('renders with initial content in the textarea', () => {
    const initialContent = '<h1>Biên bản</h1><p>Nội dung cuộc họp</p>'
    renderEditor({ initialContent })

    expect(screen.getByTestId('minutes-entry-text-0')).toHaveValue(initialContent)
  })

  it('renders with empty textarea when no initial content provided', () => {
    renderEditor()

    expect(screen.getByTestId('minutes-entry-text-0')).toHaveValue('')
  })

  it('renders fixed minutes context and does not render a conclusion textarea', () => {
    renderEditor({
      readonlySummary: {
        meetingTitle: 'Cuoc hop phan bien CNTT',
        endedAt: '2026-06-09T04:01:00.000Z',
        hostName: 'Ngo Quoc Vuong',
        secretaryName: 'Nguyen Quang Tung',
      },
      initialEntries: [
        {
          speakerName: 'Nguyen Quang Tung',
          roleLabel: 'Thu ky cuoc hop',
          timeLabel: '11:00',
          text: 'chao moi nguoi',
        },
      ],
      initialConclusion: 'Ket luan cu khong hien thanh o sua',
    })

    expect(screen.getByText('BIÊN BẢN CUỘC HỌP - BẢN NHÁP')).toBeInTheDocument()
    expect(screen.getByText('Cuộc họp: Cuoc hop phan bien CNTT')).toBeInTheDocument()
    expect(screen.getByText('Chủ tọa: Ngo Quoc Vuong')).toBeInTheDocument()
    expect(screen.getByText('Thư ký: Nguyen Quang Tung')).toBeInTheDocument()
    expect(
      screen.getByText('[11:00] Người nói: Nguyen Quang Tung | Vai trò: Thu ky cuoc hop'),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('minutes-conclusion-input')).not.toBeInTheDocument()
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

describe('MinutesEditor - role-based visibility', () => {
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

describe('MinutesEditor - submit behavior', () => {
  it('submits structured entries without conclusion', async () => {
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
    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(vi.mocked(editMinutes)).toHaveBeenCalledWith(42, {
        contentEntries: [{ speakerName: 'Nguyen Van A', roleLabel: 'Chu tri', timeLabel: '09:01', text: 'Edited speech' }],
      })
    })
  })

  it('calls editMinutes with correct meetingId and contentHtml on submit', async () => {
    vi.mocked(editMinutes).mockResolvedValue({
      data: {} as import('../../types/minutes').Minutes,
      success: true,
    })

    renderEditor({ meetingId: 42, initialContent: '<p>Nội dung</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(vi.mocked(editMinutes)).toHaveBeenCalledWith(42, {
        contentEntries: [{ speakerName: '', roleLabel: '', timeLabel: '', text: '<p>Nội dung</p>' }],
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
    await userEvent.type(textarea, '<h1>Tiêu đề</h1>')

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(vi.mocked(editMinutes)).toHaveBeenCalledWith(5, {
        contentEntries: [{ speakerName: '', roleLabel: '', timeLabel: '', text: '<h1>Tiêu đề</h1>' }],
      })
    })
  })

  it('calls onCancel when the cancel button is clicked', async () => {
    const onCancel = vi.fn()

    renderEditor({ initialContent: '<p>Content</p>', onCancel })

    await userEvent.click(screen.getByTestId('minutes-editor-cancel'))

    expect(onCancel).toHaveBeenCalledOnce()
    expect(vi.mocked(editMinutes)).not.toHaveBeenCalled()
  })
})

// â”€â”€â”€ Test 4: Loading state â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor - loading state', () => {
  it('shows loading indicator during submission', async () => {
    // Never resolves - keeps loading state
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

describe('MinutesEditor - success feedback', () => {
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

describe('MinutesEditor - error handling', () => {
  it('shows error message when API fails with a message', async () => {
    vi.mocked(editMinutes).mockRejectedValue({
      response: { data: { message: 'Bạn không có quyền chỉnh sửa biên bản' } },
    })

    renderEditor({ initialContent: '<p>Content</p>' })

    await userEvent.click(screen.getByTestId('minutes-editor-submit'))

    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-error')).toBeInTheDocument()
    })
    expect(screen.getByTestId('minutes-editor-error')).toHaveTextContent('Bạn không có quyền chỉnh sửa biên bản')
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

    // First submit - fails
    await userEvent.click(screen.getByTestId('minutes-editor-submit'))
    await waitFor(() => {
      expect(screen.getByTestId('minutes-editor-error')).toBeInTheDocument()
    })

    // Second submit - succeeds
    await userEvent.click(screen.getByTestId('minutes-editor-submit'))
    await waitFor(() => {
      expect(screen.queryByTestId('minutes-editor-error')).not.toBeInTheDocument()
    })
  })
})

// â”€â”€â”€ Test 7: Submit button disabled when content is empty â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

describe('MinutesEditor - submit button state', () => {
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

describe('MinutesEditor - accessibility', () => {
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
