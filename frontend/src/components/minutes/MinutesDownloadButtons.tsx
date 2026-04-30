/**
 * MinutesDownloadButtons — download buttons for all three minutes versions.
 *
 * - Three buttons: "Tải bản nháp", "Tải bản xác nhận", "Tải bản thư ký"
 * - Each button calls GET /api/v1/meetings/{id}/minutes/download?version=...
 * - Buttons are disabled for versions not yet available based on status
 * - Triggers browser file download using blob URL with auth header
 *
 * Requirements: 25.6
 */

import { useState } from 'react'
import { downloadMinutesPdf } from '../../services/minutesService'
import type { MinutesStatus, MinutesVersion } from '../../types/minutes'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MinutesDownloadButtonsProps {
  meetingId: number
  status: MinutesStatus
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Determine whether a given version is available based on the current status.
 */
function isVersionAvailable(version: MinutesVersion, status: MinutesStatus): boolean {
  switch (version) {
    case 'draft':
      return true
    case 'confirmed':
      return status === 'HOST_CONFIRMED' || status === 'SECRETARY_CONFIRMED'
    case 'secretary':
      return status === 'SECRETARY_CONFIRMED'
    default:
      return false
  }
}

// ─── Button config ────────────────────────────────────────────────────────────

interface VersionConfig {
  version: MinutesVersion
  label: string
  icon: string
}

const VERSION_CONFIGS: VersionConfig[] = [
  { version: 'draft', label: 'Tải bản nháp', icon: 'draft' },
  { version: 'confirmed', label: 'Tải bản xác nhận', icon: 'verified' },
  { version: 'secretary', label: 'Tải bản thư ký', icon: 'edit_document' },
]

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MinutesDownloadButtons
 *
 * Renders three download buttons for draft, confirmed, and secretary versions.
 * Disabled buttons are shown for unavailable versions with a tooltip.
 *
 * Requirements: 25.6
 */
export default function MinutesDownloadButtons({
  meetingId,
  status,
}: MinutesDownloadButtonsProps) {
  // Track which version is currently downloading
  const [downloading, setDownloading] = useState<MinutesVersion | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  const handleDownload = async (version: MinutesVersion) => {
    if (downloading) return

    setDownloading(version)
    setErrorMessage(null)

    try {
      await downloadMinutesPdf(meetingId, version)
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
        {VERSION_CONFIGS.map(({ version, label, icon }) => {
          const available = isVersionAvailable(version, status)
          const isDownloadingThis = downloading === version

          return (
            <button
              key={version}
              onClick={() => handleDownload(version)}
              disabled={!available || !!downloading}
              title={available ? label : `${label} chưa có sẵn`}
              className={`inline-flex items-center gap-2 px-3 py-2 rounded-lg
                text-label-md font-medium transition-colors
                ${available
                  ? 'bg-secondary-container text-on-secondary-container hover:bg-secondary-container/80'
                  : 'bg-surface-variant text-on-surface-variant/50 cursor-not-allowed'
                }
                disabled:opacity-60`}
              aria-label={available ? label : `${label} (chưa có sẵn)`}
              aria-disabled={!available}
              data-testid={`download-btn-${version}`}
            >
              {isDownloadingThis ? (
                <div
                  className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin"
                  aria-hidden="true"
                />
              ) : (
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                  {available ? 'download' : icon}
                </span>
              )}
              {label}
            </button>
          )
        })}
      </div>

      {/* Error message */}
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
