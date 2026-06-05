/**
 * RecordingListPage — all meeting audio segments (ASR chunks) across meetings.
 * Uses the same /audio-jobs API as the per-meeting "Ghi âm" tab.
 */

import { useEffect, useState, useCallback, useMemo } from 'react'
import { Link } from 'react-router-dom'
import api from '../services/api'
import { listAudioJobs } from '../services/meetingService'
import type { ApiResponse, PageResponse } from '../types/api'
import type { Meeting } from '../types/meeting'
import type { AudioJob } from '../services/meetingService'
import { formatJavaZonedTime } from '../utils/parseJavaZonedDateTime'
import Pagination from '../components/common/Pagination'

// ─── Types ────────────────────────────────────────────────────────────────────

interface AudioJobWithMeeting extends AudioJob {
  meetingId: number
  meetingTitle: string
}

type StatusFilter = '' | AudioJob['status']

// ─── Helpers ──────────────────────────────────────────────────────────────────

async function fetchAllMeetings(): Promise<Meeting[]> {
  const all: Meeting[] = []
  let page = 0
  let totalPages = 1
  const size = 50

  while (page < totalPages) {
    const res = await api.get<ApiResponse<PageResponse<Meeting>>>('/meetings', {
      params: { page, size, sort: 'startTime,desc' },
    })
    const data = res.data?.data
    if (!data) break
    all.push(...(data.content ?? []))
    totalPages = data.totalPages ?? 1
    page += 1
  }
  return all
}

const STATUS_COLOR: Record<string, string> = {
  COMPLETED: 'text-green-600 bg-green-50',
  FAILED: 'text-red-600 bg-red-50',
  PROCESSING: 'text-blue-600 bg-blue-50',
  QUEUED: 'text-yellow-600 bg-yellow-50',
  PENDING: 'text-slate-500 bg-slate-100',
}

const STATUS_LABEL: Record<string, string> = {
  COMPLETED: 'Hoàn thành',
  FAILED: 'Lỗi',
  PROCESSING: 'Đang xử lý',
  QUEUED: 'Chờ xử lý',
  PENDING: 'Chờ',
}

// ─── RecordingListPage ────────────────────────────────────────────────────────

export default function RecordingListPage() {
  const [allJobs, setAllJobs] = useState<AudioJobWithMeeting[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [statusFilter, setStatusFilter] = useState<StatusFilter>('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  const fetchRecordings = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const meetings = await fetchAllMeetings()

      const jobArrays = await Promise.all(
        meetings.map(async (m) => {
          try {
            const res = await listAudioJobs(m.id)
            return (res.data ?? []).map((job) => ({
              ...job,
              meetingId: m.id,
              meetingTitle: m.title,
            }))
          } catch {
            return [] as AudioJobWithMeeting[]
          }
        }),
      )

      const merged = jobArrays
        .flat()
        .sort((a, b) => {
          const ta = a.createdAt ? new Date(a.createdAt).getTime() : 0
          const tb = b.createdAt ? new Date(b.createdAt).getTime() : 0
          return tb - ta
        })

      setAllJobs(merged)
    } catch {
      setError('Không thể tải danh sách ghi âm. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchRecordings()
  }, [fetchRecordings])

  const filtered = useMemo(() => {
    if (!statusFilter) return allJobs
    return allJobs.filter((j) => j.status === statusFilter)
  }, [allJobs, statusFilter])

  const totalElements = filtered.length
  const totalPages = Math.max(1, Math.ceil(totalElements / pageSize) || 1)
  const safePage = Math.min(page, totalPages - 1)
  const pageJobs = filtered.slice(safePage * pageSize, safePage * pageSize + pageSize)

  useEffect(() => {
    if (page > totalPages - 1) setPage(Math.max(0, totalPages - 1))
  }, [page, totalPages])

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-h3 font-semibold text-on-surface">Bản ghi âm</h1>
        <p className="text-body-sm text-on-surface-variant mt-1">
          Tất cả đoạn ghi âm từ các cuộc họp (phiên họp chế độ MEETING_MODE)
        </p>
      </div>

      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
        <div className="flex items-center gap-3 flex-wrap">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Trạng thái</label>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value as StatusFilter)
                setPage(0)
              }}
              className="border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Tất cả</option>
              <option value="COMPLETED">Hoàn thành</option>
              <option value="PROCESSING">Đang xử lý</option>
              <option value="QUEUED">Chờ xử lý</option>
              <option value="PENDING">Chờ</option>
              <option value="FAILED">Thất bại</option>
            </select>
          </div>
          <button
            type="button"
            onClick={() => {
              setStatusFilter('')
              setPage(0)
            }}
            className="text-body-sm text-primary hover:underline self-end pb-2"
          >
            Xóa bộ lọc
          </button>
        </div>
      </div>

      {error && (
        <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert">
          {error}
        </div>
      )}

      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          </div>
        ) : pageJobs.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant">
            <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">mic_off</span>
            <p className="text-body-md">Không có bản ghi âm nào</p>
          </div>
        ) : (
          <ul className="divide-y divide-outline-variant">
            {pageJobs.map((job) => {
              const seqNo = String(job.sequenceNumber).padStart(2, '0')
              const dept = job.speakerDept?.trim()
              const speakerLabel = dept
                ? `${job.speakerName} - ${dept} - ${seqNo}`
                : `${job.speakerName} - ${seqNo}`
              const timeLabel = formatJavaZonedTime(job.createdAt ?? '')

              return (
                <li key={`${job.meetingId}-${job.jobId}`} className="px-4 py-4 space-y-2 hover:bg-surface-container-low transition-colors">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className="material-symbols-outlined text-[18px] text-primary shrink-0" aria-hidden="true">
                      mic
                    </span>
                    <span className="text-body-sm font-semibold text-on-surface flex-1 min-w-0 truncate">
                      {speakerLabel}
                    </span>
                    <span
                      className={`text-label-sm px-2 py-0.5 rounded-full font-medium shrink-0 ${
                        STATUS_COLOR[job.status] ?? 'text-slate-500 bg-slate-100'
                      }`}
                    >
                      {STATUS_LABEL[job.status] ?? job.status}
                    </span>
                    <span className="text-label-sm text-on-surface-variant font-mono shrink-0">{timeLabel}</span>
                  </div>

                  <div className="pl-7 flex flex-wrap items-center gap-x-3 gap-y-1 text-body-sm">
                    <span className="text-on-surface-variant">Cuộc họp:</span>
                    <Link
                      to={`/meetings/${job.meetingId}`}
                      className="text-primary hover:underline font-medium truncate max-w-md"
                    >
                      {job.meetingTitle}
                    </Link>
                  </div>

                  {job.text && (
                    <p className="text-body-sm text-on-surface-variant italic pl-7 leading-relaxed line-clamp-2">
                      &ldquo;{job.text}&rdquo;
                    </p>
                  )}

                  <div className="pl-7">
                    <Link
                      to={`/meetings/${job.meetingId}`}
                      className="inline-flex items-center gap-1 text-primary text-body-sm hover:underline"
                    >
                      <span className="material-symbols-outlined text-[16px]" aria-hidden="true">play_circle</span>
                      Nghe lại trong cuộc họp
                    </Link>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </div>

      {totalElements > 0 && (
        <Pagination
          page={safePage}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={pageSize}
          onPageChange={setPage}
          onPageSizeChange={(s) => {
            setPageSize(s)
            setPage(0)
          }}
        />
      )}
    </div>
  )
}
