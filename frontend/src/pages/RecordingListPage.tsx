/**
 * RecordingListPage — list all recordings across meetings with download support.
 * Requirements: 1.4, 1.5, 7.5, 7.6, 7.7
 */

import { useEffect, useState, useCallback } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { triggerRecordingDownload, formatFileSize } from '../services/recordingService'
import useAuthStore from '../store/authStore'
import type { ApiResponse, PageResponse } from '../types/api'
import type { Meeting, Recording, RecordingStatus } from '../types/meeting'
import { RecordingStatusBadge } from '../components/common/StatusBadge'
import Pagination from '../components/common/Pagination'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatDateTime(iso: string): string {
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

// ─── Extended recording type with meeting info ────────────────────────────────

interface RecordingWithMeeting extends Recording {
  meetingId: number
  meetingTitle: string
}

// ─── RecordingListPage ────────────────────────────────────────────────────────

export default function RecordingListPage() {
  const { user } = useAuthStore()
  const [recordings, setRecordings] = useState<RecordingWithMeeting[]>([])
  const [pageInfo, setPageInfo] = useState({ totalElements: 0, totalPages: 0, size: 10, number: 0 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [downloadingId, setDownloadingId] = useState<number | null>(null)

  // Filters
  const [statusFilter, setStatusFilter] = useState<RecordingStatus | ''>('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  const fetchRecordings = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      // Fetch meetings first, then their recordings
      const meetingsRes = await api.get<ApiResponse<PageResponse<Meeting>>>('/meetings', {
        params: { page, size: pageSize, sort: 'startTime,desc' },
      })
      const meetings = meetingsRes.data?.data?.content ?? []

      // Fetch recordings for each meeting in parallel
      const recordingArrays = await Promise.all(
        meetings.map(async (m) => {
          try {
            const res = await api.get<ApiResponse<Recording[]>>(`/meetings/${m.id}/recordings`)
            return (res.data?.data ?? []).map((r) => ({
              ...r,
              meetingId: m.id,
              meetingTitle: m.title,
            }))
          } catch {
            return []
          }
        }),
      )

      let all: RecordingWithMeeting[] = recordingArrays.flat()
      if (statusFilter) {
        all = all.filter((r) => r.status === statusFilter)
      }

      setRecordings(all)
      setPageInfo({
        totalElements: meetingsRes.data?.data?.totalElements ?? 0,
        totalPages: meetingsRes.data?.data?.totalPages ?? 0,
        size: pageSize,
        number: page,
      })
    } catch {
      setError('Không thể tải danh sách ghi âm. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, statusFilter])

  useEffect(() => {
    fetchRecordings()
  }, [fetchRecordings])

  const handleDownload = async (rec: RecordingWithMeeting) => {
    setDownloadingId(rec.id)
    try {
      await triggerRecordingDownload(rec.id, rec.fileName)
    } catch {
      // toast handled by api interceptor
    } finally {
      setDownloadingId(null)
    }
  }

  const canDelete = user?.role === 'ADMIN'

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-h3 font-semibold text-on-surface">Bản ghi âm</h1>
        <p className="text-body-sm text-on-surface-variant mt-1">
          Tất cả bản ghi âm từ các cuộc họp
        </p>
      </div>

      {/* Filters */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
        <div className="flex items-center gap-3 flex-wrap">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Trạng thái</label>
            <select
              value={statusFilter}
              onChange={(e) => { setStatusFilter(e.target.value as RecordingStatus | ''); setPage(0) }}
              className="border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Tất cả</option>
              <option value="COMPLETED">Hoàn thành</option>
              <option value="RECORDING">Đang ghi</option>
              <option value="FAILED">Thất bại</option>
            </select>
          </div>
          <button
            onClick={() => { setStatusFilter(''); setPage(0) }}
            className="text-body-sm text-primary hover:underline self-end pb-2"
          >
            Xóa bộ lọc
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert">
          {error}
        </div>
      )}

      {/* Table */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          </div>
        ) : recordings.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant">
            <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">mic_off</span>
            <p className="text-body-md">Không có bản ghi âm nào</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-body-sm">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">
                    Tên tệp
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden md:table-cell">
                    Cuộc họp
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden lg:table-cell">
                    Thời gian bắt đầu
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden lg:table-cell">
                    Kích thước
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">
                    Trạng thái
                  </th>
                  <th className="px-4 py-3" aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {recordings.map((rec, idx) => (
                  <tr
                    key={rec.id}
                    className={`border-b border-outline-variant last:border-0 hover:bg-surface-container-low
                                transition-colors ${idx % 2 === 0 ? '' : 'bg-surface-container/30'}`}
                  >
                    <td className="px-4 py-3">
                      <div className="font-medium text-on-surface truncate max-w-[200px]">
                        {rec.fileName}
                      </div>
                    </td>
                    <td className="px-4 py-3 hidden md:table-cell">
                      <Link
                        to={`/meetings/${rec.meetingId}`}
                        className="text-primary hover:underline truncate max-w-[180px] block"
                      >
                        {rec.meetingTitle}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-on-surface-variant hidden lg:table-cell whitespace-nowrap">
                      {formatDateTime(rec.startTime)}
                    </td>
                    <td className="px-4 py-3 text-on-surface-variant hidden lg:table-cell whitespace-nowrap">
                      {formatFileSize(rec.fileSize)}
                    </td>
                    <td className="px-4 py-3">
                      <RecordingStatusBadge status={rec.status} />
                    </td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        {rec.status === 'COMPLETED' && (
                          <button
                            onClick={() => handleDownload(rec)}
                            disabled={downloadingId === rec.id}
                            aria-label={`Tải xuống ${rec.fileName}`}
                            className="flex items-center gap-1 text-primary hover:underline text-body-sm
                                       disabled:opacity-60 disabled:cursor-not-allowed"
                          >
                            {downloadingId === rec.id ? (
                              <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                            ) : (
                              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">download</span>
                            )}
                            Tải xuống
                          </button>
                        )}
                        {canDelete && (
                          <Link
                            to={`/meetings/${rec.meetingId}`}
                            className="text-on-surface-variant hover:text-on-surface text-body-sm"
                            aria-label="Xem cuộc họp"
                          >
                            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">open_in_new</span>
                          </Link>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Pagination */}
      <Pagination
        page={page}
        totalPages={pageInfo.totalPages}
        totalElements={pageInfo.totalElements}
        pageSize={pageSize}
        onPageChange={setPage}
        onPageSizeChange={(s) => { setPageSize(s); setPage(0) }}
      />
    </div>
  )
}
