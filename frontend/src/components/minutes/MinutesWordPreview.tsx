import { useEffect, useState } from 'react'
import api from '../../services/api'

export interface MinutesWordPreviewProps {
  meetingId: number
  available: boolean
}

type PreviewState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'ready'; html: string }
  | { status: 'error'; message: string }

const MISSING_WORD_MESSAGE = 'Chưa có bản Word đã chỉnh sửa.'
const GENERIC_WORD_ERROR_MESSAGE = 'Không thể tải bản Word. Vui lòng thử lại sau.'

export default function MinutesWordPreview({ meetingId, available }: MinutesWordPreviewProps) {
  const [state, setState] = useState<PreviewState>({ status: 'idle' })

  useEffect(() => {
    if (!available) {
      setState({ status: 'idle' })
      return
    }

    let cancelled = false

    const fetchWordPreview = async () => {
      setState({ status: 'loading' })

      try {
        const response = await api.get<ArrayBuffer>(
          `/meetings/${meetingId}/minutes/download`,
          {
            params: { version: 'secretary', format: 'docx' },
            responseType: 'arraybuffer',
          },
        )
        const [{ default: mammoth }, { default: DOMPurify }] = await Promise.all([
          import('mammoth/mammoth.browser'),
          import('dompurify'),
        ])
        const result = await mammoth.convertToHtml({ arrayBuffer: response.data })
        const sanitizedHtml = DOMPurify.sanitize(result.value)

        if (!cancelled) {
          setState({
            status: 'ready',
            html: sanitizedHtml || '<p>Không có nội dung để hiển thị.</p>',
          })
        }
      } catch (err: unknown) {
        if (cancelled) return

        const status = (err as { response?: { status?: number } })?.response?.status
        setState({
          status: 'error',
          message: status === 404 ? MISSING_WORD_MESSAGE : GENERIC_WORD_ERROR_MESSAGE,
        })
      }
    }

    fetchWordPreview()

    return () => {
      cancelled = true
    }
  }, [available, meetingId])

  if (!available) {
    return (
      <div
        className="flex flex-col items-center justify-center h-64 rounded-lg border border-dashed border-outline-variant bg-surface-variant/30 text-on-surface-variant"
        data-testid="minutes-word-preview-unavailable"
        aria-label="Bản Word chưa có sẵn"
      >
        <span className="material-symbols-outlined text-4xl mb-2 opacity-40" aria-hidden="true">
          description
        </span>
        <p className="text-body-md">Chưa có bản Word đã chỉnh sửa</p>
      </div>
    )
  }

  if (state.status === 'loading') {
    return (
      <div
        className="flex flex-col items-center justify-center h-64 rounded-lg border border-outline-variant bg-surface-variant/30 text-on-surface-variant"
        data-testid="minutes-word-preview-loading"
        aria-label="Đang tải bản Word..."
        aria-busy="true"
      >
        <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin mb-3" />
        <p className="text-body-sm">Đang tải bản Word...</p>
      </div>
    )
  }

  if (state.status === 'error') {
    return (
      <div
        className="flex flex-col items-center justify-center h-64 rounded-lg border border-dashed border-error/40 bg-error-container/20 text-error"
        data-testid="minutes-word-preview-error"
        role="alert"
        aria-label="Lỗi khi tải bản Word"
      >
        <span className="material-symbols-outlined text-4xl mb-2" aria-hidden="true">
          error_outline
        </span>
        <p className="text-body-sm text-center px-4">{state.message}</p>
      </div>
    )
  }

  if (state.status !== 'ready') return null

  return (
    <div
      className="w-full rounded-lg overflow-hidden border border-outline-variant bg-surface-container-low"
      data-testid="minutes-word-preview"
    >
      <div className="h-[calc(100vh-260px)] min-h-[480px] overflow-auto px-4 py-6">
        <article
          className="mx-auto min-h-full max-w-[820px] bg-white px-10 py-12 text-body-md leading-relaxed text-on-surface shadow-sm [&_p]:mb-3 [&_table]:w-full [&_table]:border-collapse [&_td]:border [&_td]:border-outline-variant [&_td]:p-2 [&_th]:border [&_th]:border-outline-variant [&_th]:p-2"
          aria-label="Xem bản Word biên bản cuộc họp"
          dangerouslySetInnerHTML={{ __html: state.html }}
        />
      </div>
    </div>
  )
}
