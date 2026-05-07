/**
 * MeetingDetailPage — meeting details, members, documents, recordings,
 * attendance history, and minutes (for ENDED meetings).
 * Requirements: 3.7, 5.7, 25.4–25.6
 */

import { useEffect, useState, useCallback, useRef } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { getMeeting,
  listMeetingMembers,
  addMeetingMember,
  removeMeetingMember,
  activateMeeting,
  endMeeting,
  listAudioJobs,
  fetchAudioJobBlob,
} from '../services/meetingService'
import type { AudioJob } from '../services/meetingService'
import { formatFileSize } from '../services/recordingService'
import { getMinutes } from '../services/minutesService'
import { listAllActiveUsers } from '../services/userService'
import useAuthStore from '../store/authStore'
import api from '../services/api'
import { formatMeetingUserLabel, formatUserLabel } from '../utils/userUtils'
import type { Meeting, MeetingMember, MeetingUser, MeetingDocument, AttendanceLog, MeetingStatus } from '../types/meeting'
import type { Minutes } from '../types/minutes'
import type { ApiResponse } from '../types/api'
import MinutesViewer from '../components/minutes/MinutesViewer'
import MinutesEditor from '../components/minutes/MinutesEditor'
import MinutesConfirmDialog from '../components/minutes/MinutesConfirmDialog'
import MinutesDownloadButtons from '../components/minutes/MinutesDownloadButtons'

// ─── Helpers ──────────────────────────────────────────────────────────────────

function formatDateTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: 'Asia/Ho_Chi_Minh',
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit',
    }).format(new Date(iso))
  } catch { return iso }
}

function formatDuration(minutes?: number): string {
  if (minutes === undefined || minutes === null) return '—'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}h ${m}m` : `${m} phút`
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
        <span className="material-symbols-outlined text-[20px] text-on-surface-variant" aria-hidden="true">{icon}</span>
        <h2 className="text-body-md font-semibold text-on-surface">{title}</h2>
      </div>
      <div className="p-5">{children}</div>
    </div>
  )
}

// ─── Minutes tab ──────────────────────────────────────────────────────────────

function MinutesTab({
  meetingId,
  minutes,
  onMinutesUpdate,
  isHost,
  isSecretary,
  onShowConfirm,
}: {
  meetingId: number
  minutes: Minutes | null
  onMinutesUpdate: (m: Minutes) => void
  isHost: boolean
  isSecretary: boolean
  onShowConfirm: () => void
}) {
  const [showEditor, setShowEditor] = useState(false)

  if (!minutes) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant">
        <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">article</span>
        <p className="text-body-md">Biên bản chưa được tạo</p>
        <p className="text-body-sm mt-1">Biên bản sẽ được tạo tự động sau khi cuộc họp kết thúc.</p>
      </div>
    )
  }

  const statusLabel: Record<string, string> = {
    DRAFT: 'Bản nháp',
    HOST_CONFIRMED: 'Chủ trì đã xác nhận',
    SECRETARY_CONFIRMED: 'Đã công bố',
  }

  // Determine which version to show in viewer
  const viewerVersion =
    minutes.status === 'SECRETARY_CONFIRMED' ? 'secretary'
    : minutes.status === 'HOST_CONFIRMED' ? 'confirmed'
    : 'draft'

  return (
    <div className="space-y-4">
      {/* Status bar */}
      <div className="flex items-center justify-between flex-wrap gap-3">
        <div className="flex items-center gap-2">
          <span className="text-body-sm text-on-surface-variant">Trạng thái:</span>
          <span className="text-body-sm font-medium text-on-surface">{statusLabel[minutes.status] ?? minutes.status}</span>
        </div>
        <div className="flex items-center gap-2 flex-wrap">
          {/* Host confirm button */}
          {isHost && minutes.status === 'DRAFT' && (
            <button
              onClick={onShowConfirm}
              className="inline-flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">verified</span>
              Xác nhận biên bản
            </button>
          )}
          {/* Secretary edit button */}
          {isSecretary && minutes.status === 'HOST_CONFIRMED' && !showEditor && (
            <button
              onClick={() => setShowEditor(true)}
              className="inline-flex items-center gap-2 border border-outline-variant text-on-surface px-4 py-2 rounded-xl text-button font-medium hover:bg-surface-container transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">edit_note</span>
              Chỉnh sửa & Công bố
            </button>
          )}
          {/* Download buttons */}
          <MinutesDownloadButtons meetingId={meetingId} status={minutes.status} />
        </div>
      </div>

      {/* Editor (secretary) */}
      {showEditor && isSecretary && minutes.status === 'HOST_CONFIRMED' && (
        <MinutesEditor
          meetingId={meetingId}
          initialContent={minutes.contentHtml ?? ''}
          onSuccess={() => {
            // Re-fetch minutes after edit
            getMinutes(meetingId).then((r) => { onMinutesUpdate(r.data); setShowEditor(false) })
          }}
        />
      )}

      {/* Viewer */}
      {!showEditor && (
        <MinutesViewer meetingId={meetingId} version={viewerVersion} status={minutes.status} />
      )}
    </div>
  )
}

// ─── MeetingDetailPage ────────────────────────────────────────────────────────

type TabKey = 'info' | 'members' | 'documents' | 'recordings' | 'attendance' | 'minutes'

export default function MeetingDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { user } = useAuthStore()

  const [meeting, setMeeting] = useState<Meeting | null>(null)
  const [members, setMembers] = useState<MeetingMember[]>([])
  const [audioJobs, setAudioJobs] = useState<AudioJob[]>([])
  const [audioBlobUrls, setAudioBlobUrls] = useState<Record<string, string>>({})
  const [audioLoadingId, setAudioLoadingId] = useState<string | null>(null)
  const [documents, setDocuments] = useState<MeetingDocument[]>([])
  const [attendance, setAttendance] = useState<AttendanceLog[]>([])
  const [minutes, setMinutes] = useState<Minutes | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<TabKey>('info')
  const [lifecycleLoading, setLifecycleLoading] = useState(false)
  const [lifecycleError, setLifecycleError] = useState<string | null>(null)
  const [showConfirmDialog, setShowConfirmDialog] = useState(false)

  // Member management
  const [showMemberPicker, setShowMemberPicker] = useState(false)
  const [allUsers, setAllUsers] = useState<MeetingUser[]>([])
  const [allUsersLoading, setAllUsersLoading] = useState(false)
  const [pickerFilter, setPickerFilter] = useState('')
  const [memberActionLoading, setMemberActionLoading] = useState<number | null>(null)

  // Document upload
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [uploadingDoc, setUploadingDoc] = useState(false)
  const [docActionLoading, setDocActionLoading] = useState<number | null>(null)

  const meetingId = Number(id)

  const fetchAll = useCallback(async () => {
    if (!meetingId) return
    setLoading(true)
    setError(null)
    try {
      const [meetingRes, membersRes, audioJobsRes, docsRes, attendanceRes] = await Promise.all([
        getMeeting(meetingId),
        listMeetingMembers(meetingId),
        listAudioJobs(meetingId).catch(() => ({ data: [] as AudioJob[] })),
        api.get<ApiResponse<MeetingDocument[]>>(`/meetings/${meetingId}/documents`).catch(() => ({ data: { data: [] } })),
        api.get<ApiResponse<AttendanceLog[]>>(`/meetings/${meetingId}/attendance`).catch(() => ({ data: { data: [] } })),
      ])
      setMeeting(meetingRes.data)
      setMembers(membersRes.data ?? [])
      setAudioJobs((audioJobsRes as ApiResponse<AudioJob[]>).data ?? [])
      setDocuments((docsRes as { data: ApiResponse<MeetingDocument[]> }).data?.data ?? [])
      setAttendance((attendanceRes as { data: ApiResponse<AttendanceLog[]> }).data?.data ?? [])
      if (meetingRes.data?.status === 'ENDED') {
        getMinutes(meetingId).then((r) => setMinutes(r.data ?? null)).catch(() => {})
      }
    } catch {
      setError('Không thể tải thông tin cuộc họp.')
    } finally {
      setLoading(false)
    }
  }, [meetingId])

  useEffect(() => { fetchAll() }, [fetchAll])

  useEffect(() => {
    if (activeTab === 'minutes' && meeting?.status === 'ENDED') {
      getMinutes(meetingId).then((r) => setMinutes(r.data ?? null)).catch(() => {})
    }
  }, [activeTab, meetingId, meeting?.status])

  const isHostOrSecretary = user?.role === 'SECRETARY'
    || meeting?.hostId === user?.id
    || meeting?.secretaryId === user?.id
  const isSecretary = user?.role === 'SECRETARY'
  const canEdit = user?.role === 'SECRETARY' && meeting?.status === 'SCHEDULED'

  // Enable "Bắt đầu họp" only within 30 minutes before startTime
  const canActivate = (() => {
    if (!meeting?.startTime) return false
    const now = Date.now()
    const start = new Date(meeting.startTime).getTime()
    const thirtyMinBefore = start - 30 * 60 * 1000
    return now >= thirtyMinBefore
  })()

  // Load all users when picker opens
  useEffect(() => {
    if (!showMemberPicker || allUsers.length > 0) return
    setAllUsersLoading(true)
    listAllActiveUsers()
      .then(setAllUsers)
      .catch(() => {})
      .finally(() => setAllUsersLoading(false))
  }, [showMemberPicker, allUsers.length])

  const handleToggleMember = async (u: MeetingUser) => {
    const isMember = members.some((m) => m.userId === u.id)
    setMemberActionLoading(u.id)
    try {
      if (isMember) {
        await removeMeetingMember(meetingId, u.id)
        setMembers((prev) => prev.filter((m) => m.userId !== u.id))
      } else {
        await addMeetingMember(meetingId, u.id)
        const res = await listMeetingMembers(meetingId)
        setMembers(res.data ?? [])
      }
    } catch { /* non-critical */ }
    finally { setMemberActionLoading(null) }
  }

  const handleRemoveMember = async (userId: number) => {
    setMemberActionLoading(userId)
    try {
      await removeMeetingMember(meetingId, userId)
      setMembers((prev) => prev.filter((m) => m.userId !== userId))
    } catch { /* non-critical */ }
    finally { setMemberActionLoading(null) }
  }

  const handleUploadDocument = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    setUploadingDoc(true)
    try {
      const formData = new FormData()
      formData.append('file', file)
      await api.post(`/meetings/${meetingId}/documents`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      const res = await api.get<ApiResponse<MeetingDocument[]>>(`/meetings/${meetingId}/documents`)
      setDocuments(res.data?.data ?? [])
    } catch { /* toast handled by interceptor */ }
    finally {
      setUploadingDoc(false)
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const handleDeleteDocument = async (docId: number) => {
    setDocActionLoading(docId)
    try {
      await api.delete(`/documents/${docId}`)
      setDocuments((prev) => prev.filter((d) => d.id !== docId))
    } catch { /* non-critical */ }
    finally { setDocActionLoading(null) }
  }

  const handleDownloadDocument = (docId: number, fileName: string) => {
    const link = document.createElement('a')
    link.href = `/api/v1/documents/${docId}/download`
    link.download = fileName
    link.click()
  }

  const handleActivate = async () => {
    if (!meeting) return
    setLifecycleLoading(true)
    setLifecycleError(null)
    try {
      const res = await activateMeeting(meeting.id)
      setMeeting(res.data)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setLifecycleError(msg ?? 'Không thể kích hoạt cuộc họp.')
    } finally {
      setLifecycleLoading(false)
    }
  }

  const handleEnd = async () => {
    if (!meeting) return
    setLifecycleLoading(true)
    setLifecycleError(null)
    try {
      const res = await endMeeting(meeting.id)
      setMeeting(res.data)
      getMinutes(meetingId).then((r) => setMinutes(r.data ?? null)).catch(() => {})
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setLifecycleError(msg ?? 'Không thể kết thúc cuộc họp.')
    } finally {
      setLifecycleLoading(false)
    }
  }

  // Revoke blob URLs when unmounted to avoid memory leaks
  useEffect(() => {
    return () => {
      Object.values(audioBlobUrls).forEach((url) => URL.revokeObjectURL(url))
    }
  }, [audioBlobUrls])

  const handleLoadAudio = async (job: AudioJob) => {
    if (audioBlobUrls[job.jobId]) return // already loaded
    setAudioLoadingId(job.jobId)
    try {
      const blobUrl = await fetchAudioJobBlob(meetingId, job.jobId)
      setAudioBlobUrls((prev) => ({ ...prev, [job.jobId]: blobUrl }))
    } catch {
      // ignore — audio will show load error in browser
    } finally {
      setAudioLoadingId(null)
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
        <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">error_outline</span>
        <p className="text-body-md">{error ?? 'Không tìm thấy cuộc họp'}</p>
        <button onClick={() => navigate('/meetings')} className="mt-4 text-primary hover:underline text-body-sm">
          Quay lại danh sách
        </button>
      </div>
    )
  }

  const tabs: { key: TabKey; label: string; icon: string; count?: number }[] = [
    { key: 'info', label: 'Thông tin', icon: 'info' },
    { key: 'members', label: 'Thành viên', icon: 'group', count: members.length },
    { key: 'documents', label: 'Tài liệu', icon: 'description', count: documents.length },
    { key: 'recordings', label: 'Ghi âm', icon: 'mic', count: audioJobs.length },
    { key: 'attendance', label: 'Điểm danh', icon: 'fact_check', count: attendance.length },
    ...(meeting.status === 'ENDED' ? [{ key: 'minutes' as TabKey, label: 'Biên bản', icon: 'article' }] : []),
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
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">arrow_back</span>
            Danh sách cuộc họp
          </button>
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="text-h3 font-semibold text-on-surface truncate">{meeting.title}</h1>
            <StatusBadge status={meeting.status} />
          </div>
          {meeting.meetingCode && (
            <p className="text-body-sm text-on-surface-variant mt-1">
              Mã: <span className="font-mono font-medium">{meeting.meetingCode}</span>
            </p>
          )}
        </div>

        <div className="flex items-center gap-2 shrink-0 flex-wrap justify-end">
          {meeting.status === 'SCHEDULED' && isHostOrSecretary && (
            <div title={!canActivate ? 'Chỉ có thể bắt đầu họp trong vòng 30 phút trước giờ bắt đầu' : undefined}>
              <button
                onClick={handleActivate}
                disabled={lifecycleLoading || !canActivate}
                className="inline-flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
              >
                {lifecycleLoading
                  ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  : <span className="material-symbols-outlined text-[18px]" aria-hidden="true">play_arrow</span>}
                Bắt đầu họp
              </button>
            </div>
          )}
          {meeting.status === 'ACTIVE' && (
            <Link
              to={`/meetings/${meeting.id}/room`}
              className="inline-flex items-center gap-2 bg-green-600 text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-green-700 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">video_call</span>
              Tham gia
            </Link>
          )}
          {meeting.status === 'ACTIVE' && (isHostOrSecretary) && (
            <button
              onClick={handleEnd}
              disabled={lifecycleLoading}
              className="inline-flex items-center gap-2 bg-error text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-error/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
            >
              {lifecycleLoading
                ? <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                : <span className="material-symbols-outlined text-[18px]" aria-hidden="true">stop</span>}
              Kết thúc họp
            </button>
          )}
          {canEdit && (
            <Link
              to={`/meetings/${meeting.id}/edit`}
              className="inline-flex items-center gap-2 border border-outline-variant text-on-surface px-4 py-2 rounded-xl text-button font-medium hover:bg-surface-container transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">edit</span>
              Chỉnh sửa
            </Link>
          )}
        </div>
      </div>

      {lifecycleError && (
        <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert">
          {lifecycleError}
        </div>
      )}

      {/* Tabs */}
      <div className="border-b border-outline-variant">
        <nav className="flex gap-1 overflow-x-auto" aria-label="Tabs">
          {tabs.map((tab) => (
            <button
              key={tab.key}
              onClick={() => setActiveTab(tab.key)}
              className={`flex items-center gap-1.5 px-4 py-3 text-body-sm font-medium whitespace-nowrap border-b-2 transition-colors
                ${activeTab === tab.key
                  ? 'border-primary text-primary'
                  : 'border-transparent text-on-surface-variant hover:text-on-surface hover:border-outline-variant'}`}
              aria-selected={activeTab === tab.key}
              role="tab"
            >
              <span className="material-symbols-outlined text-[16px]" aria-hidden="true">{tab.icon}</span>
              {tab.label}
              {tab.count !== undefined && tab.count > 0 && (
                <span className="bg-surface-container-high text-on-surface-variant text-label-md px-1.5 py-0.5 rounded-full">
                  {tab.count}
                </span>
              )}
            </button>
          ))}
        </nav>
      </div>

      {/* Tab content */}
      <div role="tabpanel">
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
                  {meeting.room?.department && <span className="text-on-surface-variant"> — {meeting.room.department.name}</span>}
                </dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Chế độ</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">{meeting.mode === 'FREE_MODE' ? 'Tự do' : 'Có điều phối'}</dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Chủ trì</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">{formatMeetingUserLabel(meeting.hostId, meeting.hostName, meeting.hostDepartmentName)}</dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Thư ký</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">{formatMeetingUserLabel(meeting.secretaryId, meeting.secretaryName, meeting.secretaryDepartmentName)}</dd>
              </div>
              <div>
                <dt className="text-label-md text-on-surface-variant">Ưu tiên phiên âm</dt>
                <dd className="text-body-sm text-on-surface mt-0.5">{meeting.transcriptionPriority === 'HIGH_PRIORITY' ? 'Cao' : 'Bình thường'}</dd>
              </div>
              {meeting.description && (
                <div className="sm:col-span-2">
                  <dt className="text-label-md text-on-surface-variant">Mô tả</dt>
                  <dd className="text-body-sm text-on-surface mt-0.5 whitespace-pre-wrap">{meeting.description}</dd>
                </div>
              )}
            </dl>
          </Section>
        )}

        {activeTab === 'members' && (
          <Section title={`Thành viên (${members.length})`} icon="group">
            {/* Add member button — only when canEdit */}
            {canEdit && (
              <div className="mb-4">
                <button
                  type="button"
                  onClick={() => setShowMemberPicker((v) => !v)}
                  className="inline-flex items-center gap-2 border border-outline-variant text-on-surface px-3 py-2 rounded-lg text-body-sm font-medium hover:bg-surface-container transition-colors"
                >
                  <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
                    {showMemberPicker ? 'expand_less' : 'person_add'}
                  </span>
                  {showMemberPicker ? 'Đóng danh sách' : 'Thêm / bỏ thành viên'}
                </button>

                {/* Picker panel */}
                {showMemberPicker && (
                  <div className="mt-3 border border-outline-variant rounded-xl overflow-hidden">
                    {/* Filter input */}
                    <div className="px-3 py-2 border-b border-outline-variant bg-surface-container-low">
                      <input
                        type="text"
                        value={pickerFilter}
                        onChange={(e) => setPickerFilter(e.target.value)}
                        placeholder="Lọc theo tên hoặc username..."
                        className="w-full bg-transparent text-body-sm text-on-surface placeholder:text-on-surface-variant focus:outline-none"
                        autoFocus
                      />
                    </div>

                    {/* User list */}
                    <div className="max-h-64 overflow-y-auto">
                      {allUsersLoading ? (
                        <div className="flex items-center justify-center py-6">
                          <div className="w-5 h-5 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                        </div>
                      ) : (
                        allUsers
                          .filter((u) => {
                            const q = pickerFilter.toLowerCase()
                            return !q || u.fullName.toLowerCase().includes(q) || u.username.toLowerCase().includes(q)
                          })
                          .map((u) => {
                            const isMember = members.some((m) => m.userId === u.id)
                            const isProtected = u.id === meeting?.hostId || u.id === meeting?.secretaryId
                            const loading = memberActionLoading === u.id
                            return (
                              <button
                                key={u.id}
                                type="button"
                                onClick={() => !isProtected && handleToggleMember(u)}
                                disabled={loading || isProtected}
                                className={`w-full flex items-center gap-3 px-4 py-2.5 transition-colors text-left border-b border-outline-variant last:border-0
                                  ${isProtected ? 'opacity-60 cursor-not-allowed' : 'hover:bg-surface-container'}
                                  disabled:opacity-60`}
                                title={isProtected ? 'Không thể xóa chủ trì hoặc thư ký' : undefined}
                              >
                                {/* Checkbox visual */}
                                <div className={`w-5 h-5 rounded border-2 flex items-center justify-center shrink-0 transition-colors
                                  ${isMember ? 'bg-primary border-primary' : 'border-outline-variant'}`}>
                                  {isMember && <span className="material-symbols-outlined text-white text-[14px]">check</span>}
                                </div>
                                <div className="w-8 h-8 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                                  <span className="text-primary text-label-sm font-semibold">{u.fullName.charAt(0).toUpperCase()}</span>
                                </div>
                                <div className="flex-1 min-w-0">
                                  <div className="text-body-sm font-medium text-on-surface truncate">{formatUserLabel(u)}</div>
                                  <div className="text-label-sm text-on-surface-variant">{u.username}</div>
                                </div>
                                {isProtected && (
                                  <span className="text-label-sm text-on-surface-variant shrink-0">
                                    {u.id === meeting?.hostId ? 'Chủ trì' : 'Thư ký'}
                                  </span>
                                )}
                                {loading && <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin shrink-0" />}
                              </button>
                            )
                          })
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Current member list */}
            {members.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant text-center py-4">Chưa có thành viên nào</p>
            ) : (
              <ul className="divide-y divide-outline-variant">
                {members.map((m) => {
                  const isProtected = m.userId === meeting?.hostId || m.userId === meeting?.secretaryId
                  return (
                  <li key={m.id} className="flex items-center gap-3 py-3">
                    <div className="w-9 h-9 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                      <span className="text-primary font-semibold text-body-sm">{m.fullName.charAt(0).toUpperCase()}</span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="text-body-sm font-medium text-on-surface truncate">
                        {formatUserLabel({ id: m.userId, fullName: m.fullName, departmentName: m.departmentName })}
                      </div>
                      <div className="text-label-md text-on-surface-variant">{m.email}</div>
                    </div>
                    <span className="text-label-md text-on-surface-variant bg-surface-container px-2 py-0.5 rounded shrink-0">
                      {m.userId === meeting?.hostId ? 'Chủ trì'
                        : m.userId === meeting?.secretaryId ? 'Thư ký'
                        : 'Thành viên'}
                    </span>
                    {canEdit && !isProtected && (
                      <button
                        type="button"
                        onClick={() => handleRemoveMember(m.userId)}
                        disabled={memberActionLoading === m.userId}
                        className="p-1.5 rounded-lg hover:bg-error-container text-on-surface-variant hover:text-error transition-colors shrink-0 disabled:opacity-50"
                        aria-label={`Xóa ${m.fullName} khỏi cuộc họp`}
                      >
                        {memberActionLoading === m.userId
                          ? <div className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
                          : <span className="material-symbols-outlined text-[18px]">person_remove</span>
                        }
                      </button>
                    )}
                  </li>
                  )
                })}
              </ul>
            )}
          </Section>
        )}

        {activeTab === 'documents' && (
          <Section title={`Tài liệu (${documents.length})`} icon="description">
            {/* Upload button — any member can upload */}
            <div className="mb-4">
              <input
                ref={fileInputRef}
                type="file"
                className="hidden"
                onChange={handleUploadDocument}
                aria-label="Tải lên tài liệu"
              />
              <button
                type="button"
                onClick={() => fileInputRef.current?.click()}
                disabled={uploadingDoc}
                className="inline-flex items-center gap-2 border border-outline-variant text-on-surface px-3 py-2 rounded-lg text-body-sm font-medium hover:bg-surface-container transition-colors disabled:opacity-60 disabled:cursor-not-allowed"
              >
                {uploadingDoc
                  ? <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                  : <span className="material-symbols-outlined text-[18px]" aria-hidden="true">upload_file</span>
                }
                Tải lên tài liệu
              </button>
            </div>

            {documents.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant text-center py-4">Chưa có tài liệu nào</p>
            ) : (
              <ul className="divide-y divide-outline-variant">
                {documents.map((doc) => (
                  <li key={doc.id} className="flex items-center gap-3 py-3">
                    <span className="material-symbols-outlined text-[24px] text-on-surface-variant shrink-0" aria-hidden="true">attach_file</span>
                    <div className="flex-1 min-w-0">
                      <div className="text-body-sm font-medium text-on-surface truncate">{doc.fileName}</div>
                      <div className="text-label-md text-on-surface-variant">
                        {formatFileSize(doc.fileSize)} · {formatDateTime(doc.uploadedAt)}
                        {doc.uploadedBy && ` bởi ${doc.uploadedBy.fullName}`}
                      </div>
                    </div>
                    {/* Download */}
                    <button
                      type="button"
                      onClick={() => handleDownloadDocument(doc.id, doc.fileName)}
                      className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-primary transition-colors shrink-0"
                      aria-label={`Tải xuống ${doc.fileName}`}
                    >
                      <span className="material-symbols-outlined text-[18px]">download</span>
                    </button>
                    {/* Delete — SECRETARY only */}
                    {isSecretary && (
                      <button
                        type="button"
                        onClick={() => handleDeleteDocument(doc.id)}
                        disabled={docActionLoading === doc.id}
                        className="p-1.5 rounded-lg hover:bg-error-container text-on-surface-variant hover:text-error transition-colors shrink-0 disabled:opacity-50"
                        aria-label={`Xóa ${doc.fileName}`}
                      >
                        {docActionLoading === doc.id
                          ? <div className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
                          : <span className="material-symbols-outlined text-[18px]">delete</span>
                        }
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </Section>
        )}

        {activeTab === 'recordings' && (
          <Section title={`Ghi âm (${audioJobs.length})`} icon="mic">
            {audioJobs.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-10 text-on-surface-variant">
                <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">mic_off</span>
                <p className="text-body-md">Chưa có đoạn ghi âm nào</p>
                <p className="text-body-sm mt-1 text-center">Các đoạn âm thanh được ghi lại khi phiên họp ở chế độ MEETING_MODE và ưu tiên Cao.</p>
              </div>
            ) : (
              <ul className="divide-y divide-outline-variant">
                {audioJobs.map((job) => {
                  const seqNo = String(job.sequenceNumber).padStart(2, '0')
                  const dept = job.speakerDept?.trim()
                  const speakerLabel = dept
                    ? `${job.speakerName} - ${dept} - ${seqNo}`
                    : `${job.speakerName} - ${seqNo}`

                  const rawTime = job.createdAt ?? ''
                  const normalizedTime = /[Z]$|[+-]\d{2}:\d{2}$/.test(rawTime)
                    ? rawTime : rawTime + '+07:00'
                  const timeLabel = rawTime
                    ? new Date(normalizedTime).toLocaleTimeString('vi-VN', {
                        hour: '2-digit', minute: '2-digit', second: '2-digit',
                        timeZone: 'Asia/Ho_Chi_Minh',
                      })
                    : '—'

                  const statusColor: Record<string, string> = {
                    COMPLETED: 'text-green-600 bg-green-50',
                    FAILED: 'text-red-600 bg-red-50',
                    PROCESSING: 'text-blue-600 bg-blue-50',
                    QUEUED: 'text-yellow-600 bg-yellow-50',
                    PENDING: 'text-slate-500 bg-slate-100',
                  }
                  const statusLabel: Record<string, string> = {
                    COMPLETED: 'Hoàn thành', FAILED: 'Lỗi',
                    PROCESSING: 'Đang xử lý', QUEUED: 'Chờ xử lý', PENDING: 'Chờ',
                  }

                  const blobUrl = audioBlobUrls[job.jobId]
                  const isLoadingThis = audioLoadingId === job.jobId

                  return (
                    <li key={job.jobId} className="py-4 space-y-2">
                      {/* Header row */}
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="material-symbols-outlined text-[18px] text-primary shrink-0" aria-hidden="true">mic</span>
                        <span className="text-body-sm font-semibold text-on-surface flex-1 min-w-0 truncate">{speakerLabel}</span>
                        <span className={`text-label-sm px-2 py-0.5 rounded-full font-medium shrink-0 ${statusColor[job.status] ?? 'text-slate-500 bg-slate-100'}`}>
                          {statusLabel[job.status] ?? job.status}
                        </span>
                        <span className="text-label-sm text-on-surface-variant font-mono shrink-0">{timeLabel}</span>
                      </div>

                      {/* Transcription text */}
                      {job.text && (
                        <p className="text-body-sm text-on-surface-variant italic pl-7 leading-relaxed">
                          &ldquo;{job.text}&rdquo;
                        </p>
                      )}

                      {/* Audio player */}
                      {job.status === 'COMPLETED' && (
                        <div className="pl-7">
                          {blobUrl ? (
                            <audio
                              controls
                              src={blobUrl}
                              className="w-full h-10 rounded-lg"
                              aria-label={`Audio của ${speakerLabel}`}
                            />
                          ) : (
                            <button
                              type="button"
                              onClick={() => handleLoadAudio(job)}
                              disabled={isLoadingThis}
                              className="inline-flex items-center gap-2 border border-outline-variant text-primary px-3 py-1.5 rounded-lg text-body-sm font-medium hover:bg-surface-container transition-colors disabled:opacity-60"
                            >
                              {isLoadingThis
                                ? <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                                : <span className="material-symbols-outlined text-[18px]" aria-hidden="true">play_circle</span>}
                              {isLoadingThis ? 'Đang tải...' : 'Nghe lại'}
                            </button>
                          )}
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </Section>
        )}

        {activeTab === 'attendance' && (
          <Section title={`Lịch sử điểm danh (${attendance.length})`} icon="fact_check">
            {attendance.length === 0 ? (
              <p className="text-body-sm text-on-surface-variant text-center py-4">Chưa có dữ liệu điểm danh</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-body-sm">
                  <thead>
                    <tr className="border-b border-outline-variant">
                      <th className="text-left py-2 pr-4 text-label-md text-on-surface-variant font-semibold">Thành viên</th>
                      <th className="text-left py-2 pr-4 text-label-md text-on-surface-variant font-semibold">Giờ vào</th>
                      <th className="text-left py-2 pr-4 text-label-md text-on-surface-variant font-semibold">Giờ ra</th>
                      <th className="text-left py-2 text-label-md text-on-surface-variant font-semibold">Thời lượng</th>
                    </tr>
                  </thead>
                  <tbody>
                    {attendance.map((log) => (
                      <tr key={log.id} className="border-b border-outline-variant last:border-0">
                        <td className="py-3 pr-4 text-on-surface font-medium">
                          <div>{log.userFullName ?? '—'}</div>
                          {log.username && <div className="text-label-sm text-on-surface-variant">{log.username}</div>}
                        </td>
                        <td className="py-3 pr-4 text-on-surface-variant whitespace-nowrap">{formatDateTime(log.joinTime)}</td>
                        <td className="py-3 pr-4 text-on-surface-variant whitespace-nowrap">{log.leaveTime ? formatDateTime(log.leaveTime) : '—'}</td>
                        <td className="py-3 text-on-surface-variant">
                          {log.durationSeconds != null
                            ? formatDuration(Math.round(log.durationSeconds / 60))
                            : '—'}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Section>
        )}

        {activeTab === 'minutes' && (
          <MinutesTab
            meetingId={meetingId}
            minutes={minutes}
            onMinutesUpdate={setMinutes}
            isHost={meeting?.hostId === user?.id}
            isSecretary={isSecretary}
            onShowConfirm={() => setShowConfirmDialog(true)}
          />
        )}
      </div>

      {showConfirmDialog && (
        <MinutesConfirmDialog
          meetingId={meetingId}
          isOpen={showConfirmDialog}
          onClose={() => setShowConfirmDialog(false)}
          onSuccess={() => {
            setShowConfirmDialog(false)
            getMinutes(meetingId).then((r) => setMinutes(r.data ?? null)).catch(() => {})
          }}
        />
      )}
    </div>
  )
}
