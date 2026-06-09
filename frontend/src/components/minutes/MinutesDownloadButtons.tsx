import { useState } from 'react'
import { downloadMinutesFile } from '../../services/minutesService'
import type { Minutes, MinutesFormat, MinutesVersion } from '../../types/minutes'

export interface MinutesDownloadButtonsProps {
  meetingId: number
  minutes: Minutes
}

interface DownloadAction {
  key: string
  label: string
  version: MinutesVersion
  format: MinutesFormat
  available: boolean
  icon: string
}

function actionsFor(minutes: Minutes): DownloadAction[] {
  const editedAvailable = minutes.editedWordAvailable ?? minutes.secretaryDocxAvailable ?? !!minutes.secretaryDocxPath
  if (editedAvailable) {
    return [{ key: 'edited-word', label: 'Bản Word đã chỉnh sửa', version: 'secretary', format: 'docx', available: true, icon: 'description' }]
  }
  return [
    { key: 'raw-signed-pdf', label: 'PDF bản thô có chữ ký', version: 'confirmed', format: 'pdf', available: !!minutes.confirmedAvailable, icon: 'picture_as_pdf' },
    { key: 'raw-word', label: 'Word bản thô', version: 'draft', format: 'docx', available: !!minutes.draftDocxAvailable, icon: 'description' },
  ]
}

export default function MinutesDownloadButtons({ meetingId, minutes }: MinutesDownloadButtonsProps) {
  const [downloading, setDownloading] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleDownload = async (action: DownloadAction) => {
    if (downloading || !action.available) return
    setDownloading(action.key)
    setErrorMessage(null)
    try {
      await downloadMinutesFile(meetingId, action.version, action.format)
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Không thể tải biên bản. Vui lòng thử lại.'
      setErrorMessage(message)
    } finally {
      setDownloading(null)
    }
  }

  return (
    <div className='flex flex-col gap-3' data-testid='minutes-download-buttons' aria-label='Tải biên bản cuộc họp'>
      <div className='flex flex-wrap gap-2'>
        {actionsFor(minutes).map((action) => (
          <button
            key={action.key}
            onClick={() => handleDownload(action)}
            disabled={!action.available || !!downloading}
            className={`inline-flex items-center gap-2 px-3 py-2 rounded-lg text-label-md font-medium transition-colors ${action.available ? 'bg-secondary-container text-on-secondary-container hover:bg-secondary-container/80' : 'bg-surface-variant text-on-surface-variant/50 cursor-not-allowed'} disabled:opacity-60`}
            data-testid={`download-btn-${action.key}`}
          >
            <span className='material-symbols-outlined text-[16px]' aria-hidden='true'>{action.icon}</span>
            {downloading === action.key ? 'Đang tải...' : action.label}
          </button>
        ))}
      </div>
      {errorMessage && <p className='text-body-sm text-error' role='alert' data-testid='download-error'>{errorMessage}</p>}
    </div>
  )
}
