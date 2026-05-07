/**
 * MinutesViewer — displays a PDF inline for a specific minutes version.
 *
 * Strategy: fetch the PDF via axios (so the JWT auth header is included
 * automatically), convert to a blob object URL, then embed in an <iframe>.
 * This avoids X-Frame-Options issues caused by direct API URLs being
 * redirected to the Cloudflare landing page when auth fails.
 *
 * Requirements: 25.6
 */

import { useEffect, useState, useRef } from 'react'
import api from '../../services/api'
import type { MinutesStatus, MinutesVersion } from '../../types/minutes'

// ─── Types ────────────────────────────────────────────────────────────────────

export interface MinutesViewerProps {
  meetingId: number
  version: MinutesVersion
  status: MinutesStatus
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

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
 * Fetches the PDF via axios (with JWT auth header) and renders it as a blob
 * object URL inside an <iframe>. Shows loading / error / unavailable states.
 *
 * Requirements: 25.6
 */
export default function MinutesViewer({ meetingId, version, status }: MinutesViewerProps) {
  const available = isVersionAvailable(version, status)
  const label = VERSION_LABELS[version]

  const [blobUrl, setBlobUrl] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Track previous blob URL to revoke it on unmount / re-fetch
  const prevBlobRef = useRef<string | null>(null)

  useEffect(() => {
    if (!available) return

    let cancelled = false

    const fetchPdf = async () => {
      setLoading(true)
      setError(null)
      setBlobUrl(null)

      try {
        const response = await api.get<Blob>(
          `/meetings/${meetingId}/minutes/download`,
          {
            params: { version, inline: true },
            responseType: 'blob',
          },
        )

        if (cancelled) return

        const blob = new Blob([response.data], { type: 'application/pdf' })
        const url = URL.createObjectURL(blob)

        // Revoke previous blob URL to free memory
        if (prevBlobRef.current) {
          URL.revokeObjectURL(prevBlobRef.current)
        }
        prevBlobRef.current = url
        setBlobUrl(url)
      } catch (err: unknown) {
        if (cancelled) return
        const status = (err as { response?: { status?: number } })?.response?.status
        if (status === 404) {
          setError('Biên bản chưa được tạo hoặc đã xảy ra lỗi khi tạo biên bản.')
        } else {
          setError('Không thể tải biên bản. Vui lòng thử lại sau.')
        }
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    fetchPdf()

    return () => {
      cancelled = true
    }
  }, [available, meetingId, version])

  // Revoke blob URL on unmount
  useEffect(() => {
    return () => {
      if (prevBlobRef.current) {
        URL.revokeObjectURL(prevBlobRef.current)
      }
    }
  }, [])

  // ── Version not yet available ───────────────────────────────────────────────

  if (!available) {
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

  // ── Loading ─────────────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div
        className="flex flex-col items-center justify-center h-64 rounded-lg border border-outline-variant bg-surface-variant/30 text-on-surface-variant"
        data-testid={`minutes-viewer-loading-${version}`}
        aria-label={`Đang tải ${label}...`}
        aria-busy="true"
      >
        <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin mb-3" />
        <p className="text-body-sm">Đang tải biên bản...</p>
      </div>
    )
  }

  // ── Error ───────────────────────────────────────────────────────────────────

  if (error) {
    return (
      <div
        className="flex flex-col items-center justify-center h-64 rounded-lg border border-dashed border-error/40 bg-error-container/20 text-error"
        data-testid={`minutes-viewer-error-${version}`}
        role="alert"
        aria-label={`Lỗi khi tải ${label}`}
      >
        <span className="material-symbols-outlined text-4xl mb-2" aria-hidden="true">
          error_outline
        </span>
        <p className="text-body-sm text-center px-4">{error}</p>
      </div>
    )
  }

  // ── PDF iframe ──────────────────────────────────────────────────────────────

  if (!blobUrl) return null

  return (
    <div
      className="w-full rounded-lg overflow-hidden border border-outline-variant"
      data-testid={`minutes-viewer-${version}`}
    >
      <iframe
        src={blobUrl}
        title={`Biên bản cuộc họp — ${label}`}
        className="w-full h-[600px] border-0"
        aria-label={`Xem ${label} biên bản cuộc họp`}
        data-testid={`minutes-iframe-${version}`}
      />
    </div>
  )
}
