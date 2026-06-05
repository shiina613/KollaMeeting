import { useState } from 'react'
import { downloadMinutesFile } from '../../services/minutesService'
import type { Minutes, MinutesFormat, MinutesVersion } from '../../types/minutes'

export interface MinutesDownloadButtonsProps {
  meetingId: number
  minutes: Minutes
}

interface VersionConfig {
  version: MinutesVersion
  label: string
  unavailableIcon: string
}

interface FormatConfig {
  format: MinutesFormat
  label: string
  icon: string
}

const VERSION_CONFIGS: VersionConfig[] = [
  { version: 'draft', label: 'Bản nháp', unavailableIcon: 'draft' },
  { version: 'confirmed', label: 'Bản xác nhận', unavailableIcon: 'verified' },
  { version: 'secretary', label: 'Bản thư ký', unavailableIcon: 'edit_document' },
]

const FORMAT_CONFIGS: FormatConfig[] = [
  { format: 'pdf', label: 'PDF', icon: 'picture_as_pdf' },
  { format: 'docx', label: 'Word', icon: 'description' },
]

function isVersionPdfAvailable(version: MinutesVersion, minutes: Minutes): boolean {
  switch (version) {
    case 'draft':
      return minutes.draftAvailable ?? true
    case 'confirmed':
      return minutes.confirmedAvailable
        ?? (minutes.status === 'HOST_CONFIRMED' || minutes.status === 'SECRETARY_CONFIRMED')
    case 'secretary':
      return minutes.secretaryAvailable ?? minutes.status === 'SECRETARY_CONFIRMED'
    default:
      return false
  }
}

function isVersionDocxAvailable(version: MinutesVersion, minutes: Minutes): boolean {
  switch (version) {
    case 'draft':
      return minutes.draftDocxAvailable ?? !!minutes.draftDocxPath
    case 'confirmed':
      return (minutes.status === 'HOST_CONFIRMED' || minutes.status === 'SECRETARY_CONFIRMED')
        && (minutes.draftDocxAvailable ?? !!minutes.draftDocxPath)
    case 'secretary':
      return minutes.secretaryDocxAvailable ?? !!minutes.secretaryDocxPath
    default:
      return false
  }
}

function isDownloadAvailable(
  version: MinutesVersion,
  format: MinutesFormat,
  minutes: Minutes,
): boolean {
  return format === 'pdf'
    ? isVersionPdfAvailable(version, minutes)
    : isVersionDocxAvailable(version, minutes)
}

export default function MinutesDownloadButtons({
  meetingId,
  minutes,
}: MinutesDownloadButtonsProps) {
  const [downloading, setDownloading] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleDownload = async (version: MinutesVersion, format: MinutesFormat) => {
    if (downloading) return

    setDownloading(`${version}-${format}`)
    setErrorMessage(null)

    try {
      await downloadMinutesFile(meetingId, version, format)
    } catch (err) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Không thể tải biên bản. Vui lòng thử lại.'
      setErrorMessage(message)
    } finally {
      setDownloading(null)
    }
  }

  return (
    <div
      className="flex flex-col gap-3"
      data-testid="minutes-download-buttons"
      aria-label="Tải biên bản cuộc họp"
    >
      <div className="flex flex-wrap gap-2">
        {VERSION_CONFIGS.flatMap(({ version, label, unavailableIcon }) =>
          FORMAT_CONFIGS.map(({ format, label: formatLabel, icon }) => {
            const available = isDownloadAvailable(version, format, minutes)
            const downloadKey = `${version}-${format}`
            const isDownloadingThis = downloading === downloadKey
            const actionLabel = `${label} ${formatLabel}`

            return (
              <button
                key={downloadKey}
                onClick={() => handleDownload(version, format)}
                disabled={!available || !!downloading}
                title={available ? `Tải ${actionLabel}` : `${actionLabel} chưa có sẵn`}
                className={`inline-flex items-center gap-2 px-3 py-2 rounded-lg
                  text-label-md font-medium transition-colors
                  ${available
                    ? 'bg-secondary-container text-on-secondary-container hover:bg-secondary-container/80'
                    : 'bg-surface-variant text-on-surface-variant/50 cursor-not-allowed'
                  }
                  disabled:opacity-60`}
                aria-label={available ? `Tải ${actionLabel}` : `${actionLabel} (chưa có sẵn)`}
                aria-disabled={!available}
                data-testid={`download-btn-${version}-${format}`}
              >
                {isDownloadingThis ? (
                  <div
                    className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin"
                    aria-hidden="true"
                  />
                ) : (
                  <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                    {available ? icon : unavailableIcon}
                  </span>
                )}
                {actionLabel}
              </button>
            )
          }),
        )}
      </div>

      {errorMessage && (
        <p
          className="text-body-sm text-error"
          role="alert"
          data-testid="download-error"
        >
          {errorMessage}
        </p>
      )}
    </div>
  )
}
