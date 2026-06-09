import { useCallback, useState } from 'react'
import { editMinutes } from '../../services/minutesService'
import useAuthStore from '../../store/authStore'
import type { MinutesContentEntry } from '../../types/minutes'

export interface MinutesEditorProps {
  meetingId: number
  initialContent?: string
  initialEntries?: MinutesContentEntry[]
  initialConclusion?: string
  onSuccess?: () => void
}

function initialEntryList(initialEntries?: MinutesContentEntry[], initialContent?: string) {
  if (initialEntries && initialEntries.length > 0) return initialEntries
  return [{ speakerName: '', roleLabel: '', timeLabel: '', text: initialContent ?? '' }]
}

export default function MinutesEditor({
  meetingId,
  initialContent = '',
  initialEntries,
  initialConclusion = '',
  onSuccess,
}: MinutesEditorProps) {
  const { user } = useAuthStore()
  const [entries, setEntries] = useState<MinutesContentEntry[]>(() => initialEntryList(initialEntries, initialContent))
  const [conclusion, setConclusion] = useState(initialConclusion)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const canEdit = user?.role === 'SECRETARY' || user?.role === 'ADMIN'
  const hasContent = entries.some((entry) => entry.text.trim()) || conclusion.trim().length > 0

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
        conclusion: conclusion.trim(),
      })
      setSuccessMessage('Biên bản đã được lưu thành công.')
      onSuccess?.()
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Không thể lưu biên bản. Vui lòng thử lại.'
      setErrorMessage(message)
    } finally {
      setIsSubmitting(false)
    }
  }, [canEdit, conclusion, entries, hasContent, isSubmitting, meetingId, onSuccess])

  if (!canEdit) return null

  return (
    <form onSubmit={handleSubmit} className='flex flex-col gap-4' data-testid='minutes-editor' aria-label='Chỉnh sửa biên bản cuộc họp'>
      <div>
        <h3 className='text-body-md font-semibold text-on-surface'>Nội dung có thể chỉnh sửa</h3>
        <p className='text-body-sm text-on-surface-variant'>Thông tin cuộc họp, chủ trì, thư ký và thời gian được giữ theo hệ thống.</p>
      </div>

      {entries.map((entry, index) => (
        <div key={index} className='space-y-2 rounded-lg border border-outline-variant p-3'>
          <div className='flex flex-wrap gap-2 text-label-md text-on-surface-variant'>
            {entry.timeLabel && <span>{entry.timeLabel}</span>}
            {entry.speakerName && <span>{entry.speakerName}</span>}
            {entry.roleLabel && <span>{entry.roleLabel}</span>}
          </div>
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

      <label className='flex flex-col gap-2 text-label-lg font-medium text-on-surface'>
        Kết luận
        <textarea
          value={conclusion}
          onChange={(event) => setConclusion(event.target.value)}
          disabled={isSubmitting}
          rows={4}
          className='w-full rounded-lg border border-outline bg-surface px-3 py-2 text-body-md text-on-surface focus:outline-none focus:ring-2 focus:ring-primary disabled:opacity-60'
          data-testid='minutes-conclusion-input'
        />
      </label>

      {successMessage && <p className='text-body-md text-success bg-success/10 rounded-lg px-3 py-2' role='status' data-testid='minutes-editor-success'>{successMessage}</p>}
      {errorMessage && <p className='text-body-md text-error bg-error/10 rounded-lg px-3 py-2' role='alert' data-testid='minutes-editor-error'>{errorMessage}</p>}

      <div className='flex justify-end'>
        <button type='submit' disabled={isSubmitting || !hasContent} className='inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary text-label-lg font-medium hover:bg-primary/90 disabled:opacity-60' aria-label='Lưu biên bản' data-testid='minutes-editor-submit'>
          {isSubmitting ? 'Đang lưu...' : 'Lưu biên bản'}
        </button>
      </div>
    </form>
  )
}
