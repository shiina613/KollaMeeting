/**
 * MeetingDetailPage — display meeting details, participants, documents,
 * recordings, and attendance history.
 * Requirements: 3.7, 5.7
 */

import { useEffect, useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { getMeeting, listMeetingMembers } from '../services/meetingService'
import { listRecordings, triggerRecordingDownload, formatFileSize } from '../services/recordingService'
import useAuthStore from '../store/authStore'
import api from '../services/api'
import type { Meeting, MeetingMember, Recording, MeetingDocument, AttendanceLog, MeetingStatus } from '../types/meeting'
import type { ApiResponse } from '../types/api'

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

function formatDuration(minutes?: number): string {
  if (minutes === undefined || minutes === null) return '—'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0) return `${h}h ${m}m`
  return `${m} phút`
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
    <span className={`inline-flex items-center px-2.5 py-1 rounded-full text-label-md font-semibold ${STATUS_CLASSES[status]}`}>
      {STATUS_LABELS[status]}
    </span>
  )
}

// ─── Section wrapper ──────────────────────────────────────────────────────────

function Section({ title, icon, children }: { title: string; icon: string; children: React.ReactNode }) {
  return (
    <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
      <div className="flex items-center gap-2 px-5 py-4 border-b border-outline-variant bg-surface-container-low">
        <span className="material-symbols-outlined text-[20px] text-on-surface-variant" aria-hidden="true">
          {icon}
        </span>
        <h2 className="text-body-md font-semibold text-on-surface">{title}</h2>
      </div>
      <div className="p-5">{children}</div>
    </div>
  )
}

// ─── MeetingDetailPage ────────────────────────────────────────────────────────

type TabKey = 'info' | 'members' | 'documents' | 'recordings' | 'attendance'

export default function MeetingDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuthStore()

  const [meeting, setMeeting] = useState<Meeting | null>(null)
  const [members, setMembers] = useState<MeetingMember[]>([])
  const [recordings, setRecordings] = useState<Recording[]>([])
  const [documents, setDocuments] = useState<MeetingDocument[]>([])
  const [attendance, setAttendance] = useState<AttendanceLog[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<TabKey>('info')
  const [downloadingId, setDownloadingId] = useState<number | null>(null)

  const meetingId = Number(id)

  useEffect(() => {
    if (!meetingId) return

    setLoading(true)
    setError(null)

    Promise.all([
      getMeeting(meetingId),
      listMeetingMembers(meetingId),
      listRecordings(meetingId),
      api.get<ApiResponse<MeetingDocument[]>>(`/meetings/${meetingId}/documents`).catch(() => ({ data: { data: [] } })),
      api.get<ApiResponse<AttendanceLog[]>>(`/meetings/${meetingId}/attendance`).catch(() => ({ data: { data: [] } })),
    ])
      .then(([meetingRes, membersRes, recordingsRes, docsRes, attendanceRes]) => {
        setMeeting(meetingRes.data)
        setMembers(membersRes.data ?? [])
        setRecordings(recordingsRes.data ?? [])
        setDocuments((docsRes as { data: ApiResponse<MeetingDocument[]> }).data?.data ?? [])
        setAttendance((attendanceRes as { data: ApiResponse<AttendanceLog[]> }).data?.data ?? [])
      })
      .catch(() => {
        setError('Không thể tải thông tin cuộc họp.')
      })
      .finally(() => setLoading(false))
  }, [meetingId])

  const canEdit =
    user?.role === 'ADMIN' ||
    (user?.role === 'SECRETARY' && meeting?.status === 'SCHEDULED')

  const handleDownloadRecording = async (rec: Recording) => {
    setDownloadingId(rec.id)
    try {
      await triggerRecordingDownload(rec.id, rec.fileName)
    } catch {
      // toast handled by api interceptor
    } finally {
      setDownloadingId(null)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  if (error || !meeting) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant">
        <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">
          error_outline
        </span>
        <p className="text-body-md">{error ?? 'Không tìm thấy cuộc họp'}</p>
        <button
          onClick={() => navigate('/meetings')}
          className="mt-4 text-primary hover:underline text-body-sm"
        >
          Quay lại danh sách
        </button>
      </div>
    )
  }

  const tabs: { key: TabKey; label: string; icon: string; count?: number }[] = [
    { key: 'info', label: 'Thông tin', icon: 'info' },
    { key: 'members', label: 'Thành viên', icon: 'group', count: members.length },
    { key: 'documents', label: 'Tài liệu', icon: 'description', count: documents.length },
    { key: 'recordings', label: 'Ghi âm', icon: 'mic', count: recordings.length },
    { key: 'attendance', label: 'Điểm danh', icon: 'fact_check', count: attendance.length },
  ]

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0">
          <button
            onClick={() => navigate('/meetings')}
            className="flex items-center gap-1 text-body-sm text-on-surface-variant hover:text-on-surface mb-2"
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
              arrow_back
            </span>
            Danh sách cuộc họp
          </button>
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-h3 font-semibold text-on-surface truncate">
              {meeting.title}
            </h1>
            <StatusBadge status={meeting.status} />
          </div>
          {meeting.meetingCode && (
            <p className="text-body-sm text-on-surface-variant mt-1">
              Mã cuộc họp: <span className="font-mono font-medium">{meeting.meetingCode}</span>
            </p>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0">
          {meeting.status === 'ACTIVE' && (
            <Link
              to={`/meetings/${meeting.id}/room`}
              className="inline-flex items-center gap-2 bg-green-600 text-white px-4 py-2
                         rounded-xl text-button font-medium hover:bg-green-700 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                video_call
              </span>
              Tham gia
            </Link>
          )}
          {canEdit && (
            <Link
              to={`/meetings/${meeting.id}/edit`}
              className="inline-flex items-center gap-2 border border-outline-variant text-on-surface
                         px-4 py-2 rounded-xl text-button font-medium hover:bg-surface-container transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                edit
              </span>
              Chỉnh sửa
            </Link>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-outline-variant">
        <nav className="flex gap-1 overflow-x-auto" aria-label="Tabs">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-3 text-body-sm font-medium whitespace-nowrap
                          border-b-2 transition-colors
                          ${activeTab === tab.key
                            ? 'border-primary text-primary'
                            : 'border-transparent text-on-surface-variant hover:text-on-surface hover:border-outline-variant'
                          }`}
              aria-selected={activeTab === tab.key}
              role="tab"
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                {tab.icon}
              </span>
              {tab.label}
              {tab.count !== undefined && tab.count > 0 && (
                <span className="bg-surface-container-high text-on-surface-variant text-label-md
                                 px-1.5 py-0.5 rounded-full">
                  {tab.count}
                </span>
              )}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab content */}
      <div role="tabpanel">
        {/* Info tab */}
        {activeTab === 'info' && (
          <Section title="Thông tin cuộc họp" icon="event">
            <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-8 gap-y-4">
              <div>
                <dt className="text-label-md text-on-surface-variant">Thời gian bắt đầu</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">{formatDateTime(meeting.startTime)}</dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Thời gian kết thúc</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">{formatDateTime(meeting.endTime)}</dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Phòng họp</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">
                  {meeting.room?.name ?? meeting.roomName ?? '—'}
                  {meeting.room?.department && (
                    <span className="text-on-surface-variant"> — {meeting.room.department.name}</span>
                  )}
                </dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Chế độ</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">
                  {meeting.mode === 'FREE_MODE' ? 'Tự do' : 'Có điều phối'}
                </dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Chủ trì</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">
                  {meeting.hostUser?.fullName ?? meeting.hostUserName ?? '—'}
                </dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Thư ký</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">
                  {meeting.secretaryUser?.fullName ?? meeting.secretaryUserName ?? '—'}
                </dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Ưu tiên phiên âm</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">
                  {meeting.transcriptionPriority === 'HIGH_PRIORITY' ? 'Cao' : 'Bình thường'}
                </dd>
              </div>
              {meeting.description && (
                <div className="sm:col-span-2">
                  <dt className="text-label-md text-on-surface-variant">Mô tả</dt>
                  <dd className="text-body-sm text-on-surface mt-0.5 whitespace-pre-wrap">
                    {meeting.description}
                  </dd>
                </div>
              )}
            </dl>
          </Section>
        )}

        {/* Members tab */}
        {activeTab === 'members' && (
          <Section title={`Thành viên (${members.length})`} icon="group">
            {members.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant text-center py-4">
                Chưa có thành viên nào
              </p>
            ) : (
              <ul className="divide-y divide-outline-variant">
                {members.map((m) => (
                  <li key={m.id} className="flex items-center gap-3 py-3">
                    <div className="w-9 h-9 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                      <span className="text-primary font-semibold text-body-sm">
                        {m.fullName.charAt(0).toUpperCase()}
                      </span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-body-sm font-medium text-on-surface truncate">
                        {m.fullName}
                      </div>
                      <div className="text-label-md text-on-surface-variant">{m.email}</div>
                    </div>
                    <span className="text-label-md text-on-surface-variant bg-surface-container
                                     px-2 py-0.5 rounded shrink-0">
                      {m.role === 'ADMIN' ? 'Quản trị' : m.role === 'SECRETARY' ? 'Thư ký' : 'Thành viên'}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Section>
        )}

        {/* Documents tab */}
        {activeTab === 'documents' && (
          <Section title={`Tài liệu (${documents.length})`} icon="description">
            {documents.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant text-center py-4">
                Chưa có tài liệu nào
              </p>
            ) : (
              <ul className="divide-y divide-outline-variant">
                {documents.map((doc) => (
                  <li key={doc.id} className="flex items-center gap-3 py-3">
                    <span className="material-symbols-outlined text-[24px] text-on-surface-variant shrink-0" aria-hidden="true">
                      attach_file
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="text-body-sm font-medium text-on-surface truncate">
                        {doc.fileName}
                      </div>
                      <div className="text-label-md text-on-surface-variant">
                        {formatFileSize(doc.fileSize)} · Tải lên {formatDateTime(doc.uploadedAt)}
                        {doc.uploadedBy && ` bởi ${doc.uploadedBy.fullName}`}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Section>
        )}

        {/* Recordings tab */}
        {activeTab === 'recordings' && (
          <Section title={`Ghi âm (${recordings.length})`} icon="mic">
            {recordings.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant text-center py-4">
                Chưa có bản ghi âm nào
              </p>
            ) : (
              <ul className="divide-y divide-outline-variant">
                {recordings.map((rec) => (
                  <li key={rec.id} className="flex items-center gap-3 py-3">
                    <span
                      className={`material-symbols-outlined text-[24px] shrink-0 ${
                        rec.status === 'COMPLETED'
                          ? 'text-green-600'
                          : rec.status === 'RECORDING'
                          ? 'text-primary'
                          : 'text-error'
                      }`}
                      aria-hidden="true"
                    >
                      {rec.status === 'COMPLETED' ? 'check_circle' : rec.status === 'RECORDING' ? 'radio_button_checked' : 'error'}
                    </span>
                    <div className="flex-1 min-w-0">
                      <div className="text-body-sm font-medium text-on-surface truncate">
                        {rec.fileName}
                      </div>
                      <div className="text-label-md text-on-surface-variant">
                        {formatFileSize(rec.fileSize)} · {formatDateTime(rec.startTime)}
                        {rec.endTime && ` – ${formatDateTime(rec.endTime)}`}
                      </div>
                    </div>
                    {rec.status === 'COMPLETED' && (
                      <button
                        onClick={() => handleDownloadRecording(rec)}
                        disabled={downloadingId === rec.id}
                        className="flex items-center gap-1.5 text-primary hover:underline text-body-sm
                                   disabled:opacity-60 disabled:cursor-not-allowed shrink-0"
                        aria-label={`Tải xuống ${rec.fileName}`}
                      >
                        {downloadingId === rec.id ? (
                          <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                        ) : (
                          <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
                            download
                          </span>
                        )}
                        Tải xuống
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Section>
        )}

        {/* Attendance tab */}
        {activeTab === 'attendance' && (
          <Section title={`Lịch sử điểm danh (${attendance.length})`} icon="fact_check">
            {attendance.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant text-center py-4">
                Chưa có dữ liệu điểm danh
              </p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-body-sm">
                  <thead>
                    <tr className="border-b border-outline-variant">
                      <th className="text-left py-2 pr-4 text-label-md text-on-surface-variant font-semibold">
                        Thành viên
                      </th>
                      <th className="text-left py-2 pr-4 text-label-md text-on-surface-variant font-semibold">
                        Giờ vào
                      </th>
                      <th className="text-left py-2 pr-4 text-label-md text-on-surface-variant font-semibold">
                        Giờ ra
                      </th>
                      <th className="text-left py-2 text-label-md text-on-surface-variant font-semibold">
                        Thời lượng
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {attendance.map((log) => (
                      <tr key={log.id} className="border-b border-outline-variant last:border-0">
                        <td className="py-3 pr-4 text-on-surface font-medium">
                          {log.user?.fullName ?? '—'}
                        </td>
                        <td className="py-3 pr-4 text-on-surface-variant whitespace-nowrap">
                          {formatDateTime(log.joinTime)}
                        </td>
                        <td className="py-3 pr-4 text-on-surface-variant whitespace-nowrap">
                          {log.leaveTime ? formatDateTime(log.leaveTime) : '—'}
                        </td>
                        <td className="py-3 text-on-surface-variant">
                          {formatDuration(log.durationMinutes)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Section>
        )}
      </div>
    </div>
  )
}
