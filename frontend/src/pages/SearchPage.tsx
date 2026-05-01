/**
 * SearchPage — search meetings and transcriptions with filter controls and paginated results.
 * Requirements: 13.5, 13.6
 */

import { useState, useCallback, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { searchMeetings, searchTranscriptions } from '../services/searchService'
import { listRooms, listDepartments } from '../services/meetingService'
import type { Meeting, Room, Department } from '../types/meeting'
import type { TranscriptionSearchResult } from '../services/searchService'
import type { PageResponse } from '../types/api'
import { MeetingStatusBadge } from '../components/common/StatusBadge'
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

type SearchTab = 'meetings' | 'transcriptions'

// ─── SearchPage ───────────────────────────────────────────────────────────────

export default function SearchPage() {
  const [activeTab, setActiveTab] = useState<SearchTab>('meetings')
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')

  // Meeting filters
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [selectedRoomId, setSelectedRoomId] = useState<number | ''>('')
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number | ''>('')
  const [rooms, setRooms] = useState<Room[]>([])
  const [departments, setDepartments] = useState<Department[]>([])

  // Results
  const [meetingResults, setMeetingResults] = useState<PageResponse<Meeting> | null>(null)
  const [transcriptionResults, setTranscriptionResults] = useState<PageResponse<TranscriptionSearchResult> | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [page, setPage] = useState(0)
  const [pageSize] = useState(10)

  const inputRef = useRef<HTMLInputElement>(null)

  // Load rooms and departments for filters
  useEffect(() => {
    listRooms().then((r) => setRooms(r.data ?? [])).catch(() => {})
    listDepartments().then((r) => setDepartments(r.data ?? [])).catch(() => {})
  }, [])

  // Focus search input on mount
  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  const doSearch = useCallback(async (q: string, p: number) => {
    if (!q.trim()) return
    setLoading(true)
    setError(null)
    try {
      if (activeTab === 'meetings') {
        const res = await searchMeetings({
          query: q,
          startDate: startDate || undefined,
          endDate: endDate || undefined,
          roomId: selectedRoomId !== '' ? selectedRoomId as number : undefined,
          departmentId: selectedDepartmentId !== '' ? selectedDepartmentId as number : undefined,
          page: p,
          size: pageSize,
        })
        setMeetingResults(res.data ?? null)
      } else {
        const res = await searchTranscriptions({ query: q, page: p, size: pageSize })
        setTranscriptionResults(res.data ?? null)
      }
    } catch {
      setError('Không thể thực hiện tìm kiếm. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }, [activeTab, startDate, endDate, selectedRoomId, selectedDepartmentId, pageSize])

  // Re-search when page changes
  useEffect(() => {
    if (submittedQuery) {
      doSearch(submittedQuery, page)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!query.trim()) return
    setSubmittedQuery(query)
    setPage(0)
    setMeetingResults(null)
    setTranscriptionResults(null)
    doSearch(query, 0)
  }

  const handleTabChange = (tab: SearchTab) => {
    setActiveTab(tab)
    setPage(0)
    setMeetingResults(null)
    setTranscriptionResults(null)
    if (submittedQuery) {
      doSearch(submittedQuery, 0)
    }
  }

  const currentResults = activeTab === 'meetings' ? meetingResults : transcriptionResults
  const hasResults = currentResults !== null

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-h3 font-semibold text-on-surface">Tìm kiếm</h1>
        <p className="text-body-sm text-on-surface-variant mt-1">
          Tìm kiếm cuộc họp và nội dung phiên âm
        </p>
      </div>

      {/* Search form */}
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="flex gap-2">
          <div className="flex-1 relative">
            <span
              className="absolute left-3 top-1/2 -translate-y-1/2 material-symbols-outlined text-[20px] text-on-surface-variant"
              aria-hidden="true"
            >
              search
            </span>
            <input
              ref={inputRef}
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder={activeTab === 'meetings' ? 'Tìm kiếm cuộc họp...' : 'Tìm kiếm trong phiên âm...'}
              data-testid="search-input"
              aria-label="Từ khóa tìm kiếm"
              className="w-full border border-outline-variant rounded-xl pl-10 pr-4 py-2.5 text-body-sm
                         text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <button
            type="submit"
            disabled={!query.trim() || loading}
            data-testid="search-submit"
            className="bg-primary text-white px-5 py-2.5 rounded-xl text-button font-medium
                       hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed
                       transition-colors flex items-center gap-2"
          >
            {loading && (
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
            )}
            Tìm kiếm
          </button>
        </div>

        {/* Meeting-specific filters */}
        {activeTab === 'meetings' && (
          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              <div>
                <label className="block text-label-md text-on-surface-variant mb-1">Từ ngày</label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => setStartDate(e.target.value)}
                  className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                             text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
                />
              </div>
              <div>
                <label className="block text-label-md text-on-surface-variant mb-1">Đến ngày</label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => setEndDate(e.target.value)}
                  className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                             text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
                />
              </div>
              <div>
                <label className="block text-label-md text-on-surface-variant mb-1">Phòng ban</label>
                <select
                  value={selectedDepartmentId}
                  onChange={(e) => setSelectedDepartmentId(e.target.value === '' ? '' : Number(e.target.value))}
                  className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                             text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="">Tất cả</option>
                  {departments.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-label-md text-on-surface-variant mb-1">Phòng họp</label>
                <select
                  value={selectedRoomId}
                  onChange={(e) => setSelectedRoomId(e.target.value === '' ? '' : Number(e.target.value))}
                  className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                             text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
                >
                  <option value="">Tất cả</option>
                  {rooms.map((r) => (
                    <option key={r.id} value={r.id}>{r.name}</option>
                  ))}
                </select>
              </div>
            </div>
          </div>
        )}
      </form>

      {/* Tabs */}
      <div className="flex gap-1 border-b border-outline-variant" role="tablist">
        {(['meetings', 'transcriptions'] as SearchTab[]).map((tab) => (
          <button
            key={tab}
            role="tab"
            aria-selected={activeTab === tab}
            onClick={() => handleTabChange(tab)}
            data-testid={`search-tab-${tab}`}
            className={`inline-flex items-center gap-2 px-4 py-3 text-button font-medium border-b-2 transition-colors
              ${activeTab === tab
                ? 'border-primary text-primary'
                : 'border-transparent text-on-surface-variant hover:text-on-surface hover:border-outline-variant'
              }`}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
              {tab === 'meetings' ? 'video_chat' : 'record_voice_over'}
            </span>
            {tab === 'meetings' ? 'Cuộc họp' : 'Phiên âm'}
          </button>
        ))}
      </div>

      {/* Error */}
      {error && (
        <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert">
          {error}
        </div>
      )}

      {/* Results */}
      {!submittedQuery && !hasResults && (
        <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant">
          <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">search</span>
          <p className="text-body-md">Nhập từ khóa để bắt đầu tìm kiếm</p>
        </div>
      )}

      {loading && (
        <div className="flex items-center justify-center py-16">
          <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
        </div>
      )}

      {!loading && hasResults && activeTab === 'meetings' && meetingResults && (
        <MeetingResults results={meetingResults} />
      )}

      {!loading && hasResults && activeTab === 'transcriptions' && transcriptionResults && (
        <TranscriptionResults results={transcriptionResults} query={submittedQuery} />
      )}

      {/* Pagination */}
      {!loading && currentResults && (
        <Pagination
          page={page}
          totalPages={currentResults.totalPages}
          totalElements={currentResults.totalElements}
          pageSize={pageSize}
          onPageChange={setPage}
          onPageSizeChange={() => {}}
        />
      )}
    </div>
  )
}

// ─── MeetingResults ───────────────────────────────────────────────────────────

function MeetingResults({ results }: { results: PageResponse<Meeting> }) {
  if (results.content.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-on-surface-variant">
        <span className="material-symbols-outlined text-4xl mb-2" aria-hidden="true">event_busy</span>
        <p className="text-body-md">Không tìm thấy cuộc họp nào</p>
      </div>
    )
  }

  return (
    <div className="space-y-2" data-testid="meeting-results">
      <p className="text-body-sm text-on-surface-variant">
        {results.totalElements} kết quả
      </p>
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        <ul className="divide-y divide-outline-variant">
          {results.content.map((m) => (
            <li key={m.id}>
              <Link
                to={`/meetings/${m.id}`}
                className="flex items-center gap-4 px-5 py-4 hover:bg-surface-container-low transition-colors"
              >
                <div className="flex-1 min-w-0">
                  <div className="text-body-sm font-medium text-on-surface truncate">{m.title}</div>
                  <div className="text-label-md text-on-surface-variant mt-0.5">
                    {m.room?.name && `${m.room.name} · `}
                    {new Intl.DateTimeFormat('vi-VN', {
                      timeZone: 'Asia/Ho_Chi_Minh',
                      year: 'numeric', month: '2-digit', day: '2-digit',
                    }).format(new Date(m.startTime))}
                  </div>
                </div>
                <MeetingStatusBadge status={m.status} />
              </Link>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}

// ─── TranscriptionResults ─────────────────────────────────────────────────────

function TranscriptionResults({
  results,
  query,
}: {
  results: PageResponse<TranscriptionSearchResult>
  query: string
}) {
  if (results.content.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-12 text-on-surface-variant">
        <span className="material-symbols-outlined text-4xl mb-2" aria-hidden="true">record_voice_over</span>
        <p className="text-body-md">Không tìm thấy kết quả phiên âm nào</p>
      </div>
    )
  }

  /** Highlight matching query text */
  function highlight(text: string): React.ReactNode {
    if (!query) return text
    const regex = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi')
    const parts = text.split(regex)
    return parts.map((part, i) =>
      regex.test(part)
        ? <mark key={i} className="bg-yellow-200 text-on-surface rounded px-0.5">{part}</mark>
        : part,
    )
  }

  return (
    <div className="space-y-2" data-testid="transcription-results">
      <p className="text-body-sm text-on-surface-variant">
        {results.totalElements} kết quả
      </p>
      <div className="space-y-3">
        {results.content.map((seg) => (
          <div
            key={seg.jobId}
            className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4"
          >
            <div className="flex items-start justify-between gap-3 mb-2">
              <div>
                <Link
                  to={`/meetings/${seg.meetingId}`}
                  className="text-body-sm font-medium text-primary hover:underline"
                >
                  {seg.meetingTitle}
                </Link>
                <div className="text-label-md text-on-surface-variant mt-0.5">
                  {seg.speakerName} · {formatDateTime(seg.segmentStartTime)}
                </div>
              </div>
            </div>
            <p className="text-body-sm text-on-surface leading-relaxed">
              {highlight(seg.text)}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
