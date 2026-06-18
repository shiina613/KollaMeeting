import { useCallback, useState } from 'react'
import { editMinutes } from '../../services/minutesService'
import useAuthStore from '../../store/authStore'
import type { MinutesContentEntry } from '../../types/minutes'

export interface MinutesEditorProps {
  meetingId: number
  initialContent?: string
  initialEntries?: MinutesContentEntry[]
  initialConclusion?: string
  readonlySummary?: {
    meetingTitle: string
    endedAt?: string
    hostName?: string
    secretaryName?: string
  }
  onCancel?: () => void
  onSuccess?: () => void
}

function initialEntryList(initialEntries?: MinutesContentEntry[], initialContent?: string) {
  if (initialEntries && initialEntries.length > 0) return initialEntries
  return [{ speakerName: '', roleLabel: '', timeLabel: '', text: initialContent ?? '' }]
}

function formatLockedDateTime(iso?: string): string | null {
  if (!iso) return null
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

function formatEntryHeader(entry: MinutesContentEntry): string {
  const time = entry.timeLabel?.trim() || '--:--'
  const speaker = entry.speakerName?.trim() || 'Không xác định'
  const role = entry.roleLabel?.trim()
  return role
    ? `[${time}] Người nói: ${speaker} | Vai trò: ${role}`
    : `[${time}] Người nói: ${speaker}`
}

export default function MinutesEditor({
  meetingId,
  initialContent = '',
  initialEntries,
  readonlySummary,
  onCancel,
  onSuccess,
}: MinutesEditorProps) {
  const { user } = useAuthStore()
  const [entries, setEntries] = useState<MinutesContentEntry[]>(() => initialEntryList(initialEntries, initialContent))
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const canEdit = user?.role === 'SECRETARY' || user?.role === 'ADMIN'
  const hasContent = entries.some((entry) => entry.text.trim())

  const updateEntryText = (index: number, text: string) => {
    setEntries((current) => current.map((entry, i) => (i === index ? { ...entry, text } : entry)))
  }

  const handleSubmit = useCallback(async (event: React.FormEvent) => {
    event.preventDefault()
    if (!canEdit || isSubmitting || !hasContent) return
    setIsSubmitting(true)
    setSuccessMessage(null)
    setErrorMessage(null)
    try {
      await editMinutes(meetingId, {
        contentEntries: entries.map((entry) => ({ ...entry, text: entry.text.trim() })),
      })
      setSuccessMessage('Biên bản đã được lưu thành công.')
      onSuccess?.()
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Không thể lưu biên bản. Vui lòng thử lại.'
      setErrorMessage(message)
    } finally {
      setIsSubmitting(false)
    }
  }, [canEdit, entries, hasContent, isSubmitting, meetingId, onSuccess])

  if (!canEdit) return null

  const endedAt = formatLockedDateTime(readonlySummary?.endedAt)

  return (
    <form onSubmit={handleSubmit} className='flex flex-col gap-4' data-testid='minutes-editor' aria-label='Chỉnh sửa biên bản cuộc họp'>
      <div className='rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-4 text-body-sm text-on-surface'>
        <h3 className='mb-3 text-body-lg font-semibold text-on-surface'>BIÊN BẢN CUỘC HỌP - BẢN NHÁP</h3>
        {readonlySummary?.meetingTitle && <p>Cuộc họp: {readonlySummary.meetingTitle}</p>}
        {endedAt && <p>Kết thúc: {endedAt}</p>}
        {readonlySummary?.hostName && <p>Chủ tọa: {readonlySummary.hostName}</p>}
        {readonlySummary?.secretaryName && <p>Thư ký: {readonlySummary.secretaryName}</p>}
      </div>

      {entries.map((entry, index) => (
        <div key={index} className='space-y-2'>
          <p className='text-body-sm font-semibold text-on-surface' data-testid={`minutes-entry-header-${index}`}>
            {formatEntryHeader(entry)}
          </p>
          <textarea
            value={entry.text}
            onChange={(event) => updateEntryText(index, event.target.value)}
            disabled={isSubmitting}
            rows={4}
            className='w-full rounded-lg border border-outline bg-surface px-3 py-2 text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-60'
            aria-label={`Nội dung phát biểu ${index + 1}`}
            data-testid={`minutes-entry-text-${index}`}
          />
        </div>
      ))}

      {successMessage && <p className='text-body-md text-success bg-success/10 rounded-lg px-3 py-2' role='status' data-testid='minutes-editor-success'>{successMessage}</p>}
      {errorMessage && <p className='text-body-md text-error bg-error/10 rounded-lg px-3 py-2' role='alert' data-testid='minutes-editor-error'>{errorMessage}</p>}

      <div className='flex justify-end gap-2'>
        <button type='button' onClick={onCancel} disabled={isSubmitting} className='inline-flex items-center gap-2 px-4 py-2 rounded-lg border border-outline-variant text-on-surface text-label-lg font-medium hover:bg-surface-container disabled:opacity-60' aria-label='Hủy chỉnh sửa biên bản' data-testid='minutes-editor-cancel'>
          <span className='material-symbols-outlined text-[18px]' aria-hidden='true'>arrow_back</span>
          Hủy
        </button>
        <button type='submit' disabled={isSubmitting || !hasContent} className='inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary text-label-lg font-medium hover:bg-primary/90 disabled:opacity-60' aria-label='Lưu biên bản' data-testid='minutes-editor-submit'>
          {isSubmitting ? 'Đang lưu...' : 'Lưu biên bản'}
        </button>
      </div>
    </form>
  )
}
