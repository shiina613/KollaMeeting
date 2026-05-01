/**
 * StorageDashboard — display storage stats and bulk delete UI.
 * Requirements: 6.7
 */

import { useEffect, useState, useCallback } from 'react'
import {
  getStorageStats,
  bulkDelete,
} from '../../services/storageService'
import type { StorageStats, BulkDeleteRequest } from '../../services/storageService'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(1024))
  return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${units[i]}`
}

const FILE_TYPE_LABELS: Record<string, string> = {
  recordings: 'Bản ghi âm',
  documents: 'Tài liệu',
  audio_chunks: 'Đoạn âm thanh',
  minutes: 'Biên bản',
}

// ─── Bulk delete dialog ───────────────────────────────────────────────────────

interface BulkDeleteDialogProps {
  onClose: () => void
  onSuccess: (freedBytes: number, deletedCount: number) => void
}

function BulkDeleteDialog({ onClose, onSuccess }: BulkDeleteDialogProps) {
  const [fileType, setFileType] = useState('')
  const [olderThanDays, setOlderThanDays] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleConfirm = async () => {
    setLoading(true)
    setError(null)
    try {
      const req: BulkDeleteRequest = {}
      if (fileType) req.fileType = fileType
      if (olderThanDays) req.olderThanDays = Number(olderThanDays)
      const res = await bulkDelete(req)
      onSuccess(res.data?.freedBytes ?? 0, res.data?.deletedCount ?? 0)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg ?? 'Không thể xóa tệp. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      data-testid="bulk-delete-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="Xóa hàng loạt"
    >
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-md shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-2">Xóa hàng loạt</h2>
        <p className="text-body-sm text-on-surface-variant mb-4">
          Chọn tiêu chí để xóa tệp. Hành động này không thể hoàn tác.
        </p>
        {error && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm mb-4" role="alert" data-testid="bulk-delete-error">
            {error}
          </div>
        )}
        <div className="space-y-4">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Loại tệp</label>
            <select
              value={fileType}
              onChange={(e) => setFileType(e.target.value)}
              data-testid="bulk-delete-type-select"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Tất cả loại tệp</option>
              <option value="recordings">Bản ghi âm</option>
              <option value="documents">Tài liệu</option>
              <option value="audio_chunks">Đoạn âm thanh</option>
              <option value="minutes">Biên bản</option>
            </select>
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Cũ hơn (ngày)</label>
            <input
              type="number"
              min={1}
              value={olderThanDays}
              onChange={(e) => setOlderThanDays(e.target.value)}
              placeholder="Ví dụ: 30"
              data-testid="bulk-delete-days-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
        </div>
        <div className="flex justify-end gap-3 mt-6 pt-4 border-t border-outline-variant">
          <button
            type="button"
            onClick={onClose}
            disabled={loading}
            className="px-4 py-2 rounded-xl text-button font-medium text-on-surface border border-outline-variant hover:bg-surface-container transition-colors"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={handleConfirm}
            disabled={loading}
            data-testid="bulk-delete-confirm-btn"
            className="px-4 py-2 rounded-xl text-button font-medium bg-error text-white hover:bg-error/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
          >
            {loading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
            Xóa
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── StorageDashboard ─────────────────────────────────────────────────────────

export default function StorageDashboard() {
  const [stats, setStats] = useState<StorageStats | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [showBulkDelete, setShowBulkDelete] = useState(false)
  const [successMsg, setSuccessMsg] = useState<string | null>(null)

  const fetchStats = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await getStorageStats()
      setStats(res.data)
    } catch {
      setError('Không thể tải thống kê lưu trữ. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchStats()
  }, [fetchStats])

  const handleBulkDeleteSuccess = (freedBytes: number, deletedCount: number) => {
    setShowBulkDelete(false)
    setSuccessMsg(`Đã xóa ${deletedCount} tệp, giải phóng ${formatBytes(freedBytes)}.`)
    fetchStats()
    setTimeout(() => setSuccessMsg(null), 4000)
  }

  return (
    <div className="space-y-6" data-testid="storage-dashboard">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-h3 font-semibold text-on-surface">Lưu trữ</h2>
          <p className="text-body-sm text-on-surface-variant mt-1">Thống kê dung lượng lưu trữ hệ thống</p>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={fetchStats}
            disabled={loading}
            data-testid="refresh-stats-btn"
            aria-label="Làm mới thống kê"
            className="p-2 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-on-surface transition-colors disabled:opacity-40"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">refresh</span>
          </button>
          <button
            onClick={() => setShowBulkDelete(true)}
            data-testid="bulk-delete-btn"
            className="inline-flex items-center gap-2 bg-error text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-error/90 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">delete_sweep</span>
            Xóa hàng loạt
          </button>
        </div>
      </div>

      {/* Success message */}
      {successMsg && (
        <div className="bg-green-100 text-green-700 rounded-xl px-4 py-3 text-body-sm" data-testid="bulk-delete-success">
          {successMsg}
        </div>
      )}

      {/* Error */}
      {error && (
        <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert" data-testid="storage-error">
          {error}
        </div>
      )}

      {/* Stats */}
      {loading ? (
        <div className="flex items-center justify-center py-16">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
        </div>
      ) : stats ? (
        <div className="space-y-4">
          {/* Total */}
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6" data-testid="total-storage-card">
            <div className="text-label-md text-on-surface-variant mb-1">Tổng dung lượng</div>
            <div className="text-h3 font-semibold text-on-surface" data-testid="total-storage-size">
              {formatBytes(stats.totalSize)}
            </div>
          </div>

          {/* Breakdown */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            {(stats.breakdown ?? []).map((item) => (
              <div
                key={item.type}
                className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4"
                data-testid={`storage-type-${item.type}`}
              >
                <div className="text-label-md text-on-surface-variant mb-1">
                  {FILE_TYPE_LABELS[item.type] ?? item.type}
                </div>
                <div className="text-body-md font-semibold text-on-surface" data-testid={`storage-size-${item.type}`}>
                  {formatBytes(item.totalSize)}
                </div>
                <div className="text-label-md text-on-surface-variant mt-0.5">
                  {item.count} tệp
                </div>
              </div>
            ))}
          </div>
        </div>
      ) : null}

      {/* Bulk delete dialog */}
      {showBulkDelete && (
        <BulkDeleteDialog
          onClose={() => setShowBulkDelete(false)}
          onSuccess={handleBulkDeleteSuccess}
        />
      )}
    </div>
  )
}
