import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MinutesWordPreview from './MinutesWordPreview'
import api from '../../services/api'
import mammoth from 'mammoth/mammoth.browser'
import DOMPurify from 'dompurify'

const apiMocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

const mammothMocks = vi.hoisted(() => ({
  convertToHtml: vi.fn(),
}))

const domPurifyMocks = vi.hoisted(() => ({
  sanitize: vi.fn((html: string) => html),
}))

vi.mock('../../services/api', () => ({ default: apiMocks }))
vi.mock('mammoth/mammoth.browser', () => ({ default: mammothMocks }))
vi.mock('dompurify', () => ({ default: domPurifyMocks }))

beforeEach(() => {
  vi.clearAllMocks()
})

describe('MinutesWordPreview', () => {
  it('shows an unavailable state and does not fetch when the edited Word file is missing', () => {
    render(<MinutesWordPreview meetingId={42} available={false} />)

    expect(screen.getByTestId('minutes-word-preview-unavailable')).toHaveTextContent(
      'Chưa có bản Word đã chỉnh sửa',
    )
    expect(api.get).not.toHaveBeenCalled()
  })

  it('shows a loading state while the Word file is being fetched', async () => {
    vi.mocked(api.get).mockImplementation(
      () => new Promise(() => undefined) as ReturnType<typeof api.get>,
    )

    render(<MinutesWordPreview meetingId={42} available />)

    expect(await screen.findByTestId('minutes-word-preview-loading')).toBeInTheDocument()
  })

  it('fetches secretary DOCX bytes and renders sanitized HTML', async () => {
    const docxBytes = new ArrayBuffer(8)
    vi.mocked(api.get).mockResolvedValue({ data: docxBytes })
    vi.mocked(mammoth.convertToHtml).mockResolvedValue({
      value: '<p>Biên bản đã chỉnh sửa</p>',
      messages: [],
    })
    vi.mocked(DOMPurify.sanitize).mockReturnValue('<p>Biên bản đã chỉnh sửa</p>')

    render(<MinutesWordPreview meetingId={42} available />)

    expect(await screen.findByText('Biên bản đã chỉnh sửa')).toBeInTheDocument()
    expect(api.get).toHaveBeenCalledWith('/meetings/42/minutes/download', {
      params: { version: 'secretary', format: 'docx' },
      responseType: 'arraybuffer',
    })
    expect(mammoth.convertToHtml).toHaveBeenCalledWith({ arrayBuffer: docxBytes })
    expect(DOMPurify.sanitize).toHaveBeenCalledWith('<p>Biên bản đã chỉnh sửa</p>')
  })

  it('shows a missing Word message for 404 responses', async () => {
    vi.mocked(api.get).mockRejectedValue({ response: { status: 404 } })

    render(<MinutesWordPreview meetingId={42} available />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Chưa có bản Word đã chỉnh sửa.',
    )
  })

  it('shows a retryable error for conversion failures', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: new ArrayBuffer(8) })
    vi.mocked(mammoth.convertToHtml).mockRejectedValue(new Error('bad docx'))

    render(<MinutesWordPreview meetingId={42} available />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không thể tải bản Word. Vui lòng thử lại sau.',
    )
  })
})
