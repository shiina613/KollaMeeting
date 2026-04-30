/**
 * MinutesViewer — displays a PDF viewer (iframe) for a specific minutes version.
 *
 * - Shows the PDF in an iframe using the authenticated download URL
 * - Shows a placeholder message if the version is not yet available
 *
 * Requirements: 25.6
 */

import { useMemo } from 'react'
import { getMinutesDownloadUrl } from '../../services/minutesService'
import type { MinutesStatus, MinutesVersion } from '../../types/minutes'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MinutesViewerProps {
  meetingId: number
  version: MinutesVersion
  status: MinutesStatus
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Determine whether a given version is available based on the current status.
 * - draft: always available once minutes exist (status is at least DRAFT)
 * - confirmed: available when status is HOST_CONFIRMED or SECRETARY_CONFIRMED
 * - secretary: available only when status is SECRETARY_CONFIRMED
 */
function isVersionAvailable(version: MinutesVersion, status: MinutesStatus): boolean {
  switch (version) {
    case 'draft':
      return true // DRAFT status means draft PDF exists
    case 'confirmed':
      return status === 'HOST_CONFIRMED' || status === 'SECRETARY_CONFIRMED'
    case 'secretary':
      return status === 'SECRETARY_CONFIRMED'
    default:
      return false
  }
}

const VERSION_LABELS: Record<MinutesVersion, string> = {
  draft: 'Bản nháp',
  confirmed: 'Bản xác nhận',
  secretary: 'Bản thư ký',
}

const UNAVAILABLE_MESSAGES: Record<MinutesVersion, string> = {
  draft: 'Chưa có biên bản nháp',
  confirmed: 'Chưa có biên bản xác nhận',
  secretary: 'Chưa có biên bản thư ký',
}

// ─── Component ────────────────────────────────────────────────────────────────

/**
 * MinutesViewer
 *
 * Renders an iframe with the PDF for the given version, or a placeholder
 * message if the version is not yet available.
 *
 * Requirements: 25.6
 */
export default function MinutesViewer({ meetingId, version, status }: MinutesViewerProps) {
  const available = isVersionAvailable(version, status)

  const pdfUrl = useMemo(() => {
    if (!available) return null
    return getMinutesDownloadUrl(meetingId, version)
  }, [available, meetingId, version])

  const label = VERSION_LABELS[version]

  // ── Unavailable state ──────────────────────────────────────────────────────

  if (!available || !pdfUrl) {
    return (
      <div
        className="flex flex-col items-center justify-center h-64 rounded-lg border border-dashed border-outline-variant bg-surface-variant/30 text-on-surface-variant"
        data-testid={`minutes-viewer-unavailable-${version}`}
        aria-label={`${label} chưa có sẵn`}
      >
        <span className="material-symbols-outlined text-4xl mb-2 opacity-40" aria-hidden="true">
          description
        </span>
        <p className="text-body-md">{UNAVAILABLE_MESSAGES[version]}</p>
      </div>
    )
  }

  // ── PDF iframe ─────────────────────────────────────────────────────────────

  return (
    <div
      className="w-full rounded-lg overflow-hidden border border-outline-variant"
      data-testid={`minutes-viewer-${version}`}
    >
      <iframe
        src={pdfUrl}
        title={`Biên bản cuộc họp — ${label}`}
        className="w-full h-[600px] border-0"
        aria-label={`Xem ${label} biên bản cuộc họp`}
        data-testid={`minutes-iframe-${version}`}
      />
    </div>
  )
}
