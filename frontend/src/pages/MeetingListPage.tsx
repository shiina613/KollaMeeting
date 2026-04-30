/**
 * MeetingListPage — list meetings with filters, sort, and pagination.
 * Requirements: 3.4, 13.5, 13.6
 */

import { useEffect, useState, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import useAuthStore from '../store/authStore'
import {
  listMeetings,
  listRooms,
  listDepartments,
} from '../services/meetingService'
import type { Meeting, MeetingFilters, MeetingStatus, Room, Department } from '../types/meeting'
import type { PageResponse } from '../types/api'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Format ISO8601 datetime to UTC+7 display string */
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

// ─── Status badge ─────────────────────────────────────────────────────────────

const STATUS_LABELS: Record<MeetingStatus, string> = {
  SCHEDULED: 'Đã lên lịch',
  ACTIVE: 'Đang diễn ra',
  ENDED: 'Đã kết thúc',
}

const STATUS_CLASSES: Record<MeetingStatus, string> = {
  SCHEDULED: 'bg-blue-100 text-blue-700',
  ACTIVE: 'bg-green-100 text-green-700',
  ENDED: 'bg-slate-100 text-slate-600',
}

function StatusBadge({ status }: { status: MeetingStatus }) {
  return (
    <span
      className={`inline-flex items-center px-2 py-0.5 rounded text-label-md font-semibold ${STATUS_CLASSES[status]}`}
    >
      {STATUS_LABELS[status]}
    </span>
  )
}

// ─── Page size options ────────────────────────────────────────────────────────

const PAGE_SIZE_OPTIONS = [10, 20, 50]

// ─── MeetingListPage ──────────────────────────────────────────────────────────

export default function MeetingListPage() {
  const { user } = useAuthStore()
  const navigate = useNavigate()

  // Data state
  const [meetings, setMeetings] = useState<Meeting[]>([])
  const [pageInfo, setPageInfo] = useState<Omit<PageResponse<Meeting>, 'content'>>({
    totalElements: 0,
    totalPages: 0,
    size: 10,
    number: 0,
  })
  const [rooms, setRooms] = useState<Room[]>([])
  const [departments, setDepartments] = useState<Department[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Filter state
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [selectedRoomId, setSelectedRoomId] = useState<number | ''>('')
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | ''>('')
  const [selectedStatus, setSelectedStatus] = useState<MeetingStatus | ''>('')
  const [sort, setSort] = useState('startTime,desc')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Load rooms and departments once
  useEffect(() => {
    listRooms()
      .then((res) => setRooms(res.data ?? []))
      .catch(() => {/* non-critical */})
    listDepartments()
      .then((res) => setDepartments(res.data ?? []))
      .catch(() => {/* non-critical */})
  }, [])

  // Fetch meetings
  const fetchMeetings = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const filters: MeetingFilters = {
        page,
        size: pageSize,
        sort,
      }
      if (startDate) filters.startDate = startDate
      if (endDate) filters.endDate = endDate
      if (selectedRoomId !== '') filters.roomId = selectedRoomId as number
      if (selectedDepartmentId !== '') filters.departmentId = selectedDepartmentId as number
      if (selectedStatus !== '') filters.status = selectedStatus as MeetingStatus

      const res = await listMeetings(filters)
      const pageData = res.data
      setMeetings(pageData?.content ?? [])
      setPageInfo({
        totalElements: pageData?.totalElements ?? 0,
        totalPages: pageData?.totalPages ?? 0,
        size: pageData?.size ?? pageSize,
        number: pageData?.number ?? 0,
      })
    } catch {
      setError('Không thể tải danh sách cuộc họp. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, sort, startDate, endDate, selectedRoomId, selectedDepartmentId, selectedStatus])

  useEffect(() => {
    fetchMeetings()
  }, [fetchMeetings])

  // Reset to page 0 when filters change
  const handleFilterChange = () => {
    setPage(0)
  }

  const canCreate = user?.role === 'ADMIN' || user?.role === 'SECRETARY'

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-h3 font-semibold text-on-surface">Danh sách cuộc họp</h1>
          <p className="text-body-sm text-on-surface-variant mt-1">
            {pageInfo.totalElements > 0
              ? `${pageInfo.totalElements} cuộc họp`
              : 'Không có cuộc họp nào'}
          </p>
        </div>
        {canCreate && (
          <Link
            to="/meetings/new"
            className="inline-flex items-center gap-2 bg-primary text-white px-4 py-2
                       rounded-xl text-button font-medium hover:bg-primary/90 transition-colors"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
              add
            </span>
            Tạo cuộc họp
          </Link>
        )}
      </div>

      {/* Filters */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 gap-3">
          {/* Start date */}
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Từ ngày
            </label>
            <input
              type="date"
              value={startDate}
              onChange={(e) => { setStartDate(e.target.value); handleFilterChange() }}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          {/* End date */}
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Đến ngày
            </label>
            <input
              type="date"
              value={endDate}
              onChange={(e) => { setEndDate(e.target.value); handleFilterChange() }}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          {/* Department filter */}
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Phòng ban
            </label>
            <select
              value={selectedDepartmentId}
              onChange={(e) => {
                setSelectedDepartmentId(e.target.value === '' ? '' : Number(e.target.value))
                handleFilterChange()
              }}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Tất cả phòng ban</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </select>
          </div>

          {/* Room filter */}
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Phòng họp
            </label>
            <select
              value={selectedRoomId}
              onChange={(e) => {
                setSelectedRoomId(e.target.value === '' ? '' : Number(e.target.value))
                handleFilterChange()
              }}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Tất cả phòng</option>
              {rooms.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>

          {/* Status filter */}
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Trạng thái
            </label>
            <select
              value={selectedStatus}
              onChange={(e) => {
                setSelectedStatus(e.target.value as MeetingStatus | '')
                handleFilterChange()
              }}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Tất cả trạng thái</option>
              <option value="SCHEDULED">Đã lên lịch</option>
              <option value="ACTIVE">Đang diễn ra</option>
              <option value="ENDED">Đã kết thúc</option>
            </select>
          </div>
        </div>

        {/* Sort + clear */}
        <div className="flex items-center justify-between mt-3 pt-3 border-t border-outline-variant">
          <div className="flex items-center gap-2">
            <label className="text-label-md text-on-surface-variant">Sắp xếp:</label>
            <select
              value={sort}
              onChange={(e) => { setSort(e.target.value); setPage(0) }}
              className="border border-outline-variant rounded-lg px-3 py-1.5 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="startTime,desc">Thời gian bắt đầu (mới nhất)</option>
              <option value="startTime,asc">Thời gian bắt đầu (cũ nhất)</option>
              <option value="title,asc">Tên A–Z</option>
              <option value="title,desc">Tên Z–A</option>
            </select>
          </div>
          <button
            onClick={() => {
              setStartDate('')
              setEndDate('')
              setSelectedRoomId('')
              setSelectedDepartmentId('')
              setSelectedStatus('')
              setSort('startTime,desc')
              setPage(0)
            }}
            className="text-body-sm text-primary hover:underline"
          >
            Xóa bộ lọc
          </button>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm">
          {error}
        </div>
      )}

      {/* Table */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          </div>
        ) : meetings.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant">
            <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">
              event_busy
            </span>
            <p className="text-body-md">Không có cuộc họp nào phù hợp</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-body-sm">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">
                    Tiêu đề
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden md:table-cell">
                    Phòng họp
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden lg:table-cell">
                    Thời gian bắt đầu
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden lg:table-cell">
                    Thời gian kết thúc
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">
                    Trạng thái
                  </th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden xl:table-cell">
                    Chủ trì
                  </th>
                  <th className="px-4 py-3" aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {meetings.map((meeting, idx) => (
                  <tr
                    key={meeting.id}
                    className={`border-b border-outline-variant last:border-0 hover:bg-surface-container-low
                                transition-colors cursor-pointer ${idx % 2 === 0 ? '' : 'bg-surface-container/30'}`}
                    onClick={() => navigate(`/meetings/${meeting.id}`)}
                  >
                    <td className="px-4 py-3">
                      <div className="font-medium text-on-surface line-clamp-1">
                        {meeting.title}
                      </div>
                      {meeting.meetingCode && (
                        <div className="text-label-md text-on-surface-variant mt-0.5">
                          #{meeting.meetingCode}
                        </div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-on-surface-variant hidden md:table-cell">
                      {meeting.room?.name ?? meeting.roomName ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-on-surface-variant hidden lg:table-cell whitespace-nowrap">
                      {formatDateTime(meeting.startTime)}
                    </td>
                    <td className="px-4 py-3 text-on-surface-variant hidden lg:table-cell whitespace-nowrap">
                      {formatDateTime(meeting.endTime)}
                    </td>
                    <td className="px-4 py-3">
                      <StatusBadge status={meeting.status} />
                    </td>
                    <td className="px-4 py-3 text-on-surface-variant hidden xl:table-cell">
                      {meeting.hostUser?.fullName ?? meeting.hostUserName ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <button
                        onClick={(e) => {
                          e.stopPropagation()
                          navigate(`/meetings/${meeting.id}`)
                        }}
                        className="text-primary hover:underline text-body-sm"
                        aria-label={`Xem chi tiết ${meeting.title}`}
                      >
                        Chi tiết
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Pagination */}
      {pageInfo.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-body-sm text-on-surface-variant">
            <span>Hiển thị</span>
            <select
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0) }}
              className="border border-outline-variant rounded-lg px-2 py-1 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              {PAGE_SIZE_OPTIONS.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
            <span>/ trang</span>
          </div>

          <div className="flex items-center gap-1">
            <button
              onClick={() => setPage(0)}
              disabled={page === 0}
              className="p-2 rounded-lg hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Trang đầu"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                first_page
              </span>
            </button>
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="p-2 rounded-lg hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Trang trước"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                chevron_left
              </span>
            </button>

            {/* Page numbers */}
            {Array.from({ length: Math.min(5, pageInfo.totalPages) }, (_, i) => {
              const startPage = Math.max(0, Math.min(page - 2, pageInfo.totalPages - 5))
              const pageNum = startPage + i
              return (
                <button
                  key={pageNum}
                  onClick={() => setPage(pageNum)}
                  className={`w-9 h-9 rounded-lg text-body-sm font-medium transition-colors
                    ${pageNum === page
                      ? 'bg-primary text-white'
                      : 'hover:bg-surface-container text-on-surface'
                    }`}
                  aria-label={`Trang ${pageNum + 1}`}
                  aria-current={pageNum === page ? 'page' : undefined}
                >
                  {pageNum + 1}
                </button>
              )
            })}

            <button
              onClick={() => setPage((p) => Math.min(pageInfo.totalPages - 1, p + 1))}
              disabled={page >= pageInfo.totalPages - 1}
              className="p-2 rounded-lg hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Trang sau"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                chevron_right
              </span>
            </button>
            <button
              onClick={() => setPage(pageInfo.totalPages - 1)}
              disabled={page >= pageInfo.totalPages - 1}
              className="p-2 rounded-lg hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Trang cuối"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                last_page
              </span>
            </button>
          </div>

          <div className="text-body-sm text-on-surface-variant">
            Trang {page + 1} / {pageInfo.totalPages}
          </div>
        </div>
      )}
    </div>
  )
}
