/**
 * DashboardPage — overview of upcoming meetings and recent activity.
 * Requirements: 1.4, 1.5
 */

import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listMeetings } from '../services/meetingService'
import useAuthStore from '../store/authStore'
import type { Meeting, MeetingStatus } from '../types/meeting'
import { MeetingStatusBadge } from '../components/common/StatusBadge'

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

// ─── Stat card ────────────────────────────────────────────────────────────────

interface StatCardProps {
  label: string
  value: number | string
  icon: string
  color: string
}

function StatCard({ label, value, icon, color }: StatCardProps) {
  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-5 flex items-center gap-4">
      <div className={`w-12 h-12 rounded-xl flex items-center justify-center shrink-0 ${color}`}>
        <span className="material-symbols-outlined text-[24px]" aria-hidden="true">{icon}</span>
      </div>
      <div>
        <div className="text-h3 font-semibold text-on-surface">{value}</div>
        <div className="text-body-sm text-on-surface-variant">{label}</div>
      </div>
    </div>
  )
}

// ─── DashboardPage ────────────────────────────────────────────────────────────

export default function DashboardPage() {
  const { user } = useAuthStore()
  const [activeMeetings, setActiveMeetings] = useState<Meeting[]>([])
  const [upcomingMeetings, setUpcomingMeetings] = useState<Meeting[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false

    Promise.all([
      listMeetings({ status: 'ACTIVE' as MeetingStatus, size: 5, sort: 'startTime,asc' }),
      listMeetings({ status: 'SCHEDULED' as MeetingStatus, size: 5, sort: 'startTime,asc' }),
    ])
      .then(([activeRes, scheduledRes]) => {
        if (cancelled) return
        setActiveMeetings(activeRes.data?.content ?? [])
        setUpcomingMeetings(scheduledRes.data?.content ?? [])
      })
      .catch(() => {/* non-critical */})
      .finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }
  }, [])

  const totalActive = activeMeetings.length
  const totalUpcoming = upcomingMeetings.length

  return (
    <div className="space-y-6">
      {/* Greeting */}
      <div>
        <h1 className="text-h3 font-semibold text-on-surface">
          Xin chào, {user?.username ?? 'bạn'} 👋
        </h1>
        <p className="text-body-sm text-on-surface-variant mt-1">
          Đây là tổng quan hoạt động của bạn hôm nay.
        </p>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard
          label="Cuộc họp đang diễn ra"
          value={loading ? '—' : totalActive}
          icon="video_call"
          color="bg-green-100 text-green-700"
        />
        <StatCard
          label="Cuộc họp sắp tới"
          value={loading ? '—' : totalUpcoming}
          icon="event"
          color="bg-blue-100 text-blue-700"
        />
        <StatCard
          label="Vai trò"
          value={user?.role === 'ADMIN' ? 'Quản trị viên' : user?.role === 'SECRETARY' ? 'Thư ký' : 'Người dùng'}
          icon="badge"
          color="bg-purple-100 text-purple-700"
        />
      </div>

      {/* Active meetings */}
      {!loading && activeMeetings.length > 0 && (
        <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
          <div className="flex items-center justify-between px-5 py-4 border-b border-outline-variant bg-surface-container-low">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse" />
              <h2 className="text-body-md font-semibold text-on-surface">Đang diễn ra</h2>
            </div>
            <Link to="/meetings" className="text-body-sm text-primary hover:underline">
              Xem tất cả
            </Link>
          </div>
          <ul className="divide-y divide-outline-variant">
            {activeMeetings.map((m) => (
              <MeetingRow key={m.id} meeting={m} />
            ))}
          </ul>
        </div>
      )}

      {/* Upcoming meetings */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-outline-variant bg-surface-container-low">
          <h2 className="text-body-md font-semibold text-on-surface">Cuộc họp sắp tới</h2>
          <Link to="/meetings" className="text-body-sm text-primary hover:underline">
            Xem tất cả
          </Link>
        </div>
        {loading ? (
          <div className="flex items-center justify-center py-10">
            <div className="w-6 h-6 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          </div>
        ) : upcomingMeetings.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-10 text-on-surface-variant">
            <span className="material-symbols-outlined text-4xl mb-2" aria-hidden="true">event_available</span>
            <p className="text-body-sm">Không có cuộc họp nào sắp tới</p>
          </div>
        ) : (
          <ul className="divide-y divide-outline-variant">
            {upcomingMeetings.map((m) => (
              <MeetingRow key={m.id} meeting={m} />
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}

// ─── MeetingRow ───────────────────────────────────────────────────────────────

function MeetingRow({ meeting }: { meeting: Meeting }) {
  return (
    <li>
      <Link
        to={`/meetings/${meeting.id}`}
        className="flex items-center gap-4 px-5 py-4 hover:bg-surface-container-low transition-colors"
      >
        <div className="flex-1 min-w-0">
          <div className="text-body-sm font-medium text-on-surface truncate">{meeting.title}</div>
          <div className="text-label-md text-on-surface-variant mt-0.5">
            {formatDateTime(meeting.startTime)}
            {meeting.room?.name && ` · ${meeting.room.name}`}
          </div>
        </div>
        <MeetingStatusBadge status={meeting.status} />
        {meeting.status === 'ACTIVE' && (
          <span className="material-symbols-outlined text-[18px] text-green-600 shrink-0" aria-hidden="true">
            arrow_forward
          </span>
        )}
      </Link>
    </li>
  )
}
