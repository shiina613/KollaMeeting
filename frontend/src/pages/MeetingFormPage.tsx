/**
 * MeetingFormPage — create or edit a meeting.
 * Protected: ADMIN and SECRETARY only.
 * Requirements: 3.3, 3.8, 12.8
 */

import { useEffect, useState, useCallback } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  createMeeting,
  updateMeeting,
  getMeeting,
  listRooms,
  getRoomAvailability,
  isSchedulingConflict,
  getConflictMessage,
} from '../services/meetingService'
import { listHostCandidates, listSecretaryCandidates, searchUsers } from '../services/userService'
import { listMeetingMembers, addMeetingMember, removeMeetingMember } from '../services/meetingService'
import { formatUserLabel } from '../utils/userUtils'
import type { Room, MeetingUser, MeetingMember, TranscriptionPriority } from '../types/meeting'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Convert local datetime-local input value to ISO8601 string for backend (UTC+7, no offset) */
function localToIso(local: string): string {
  if (!local) return ''
  // datetime-local gives "YYYY-MM-DDTHH:mm"
  // Backend uses LocalDateTime (UTC+7) — send as-is without timezone conversion
  return local + ':00'
}

/** Convert ISO8601 to datetime-local input value (UTC+7) */
function isoToLocal(iso: string): string {
  if (!iso) return ''
  try {
    const date = new Date(iso)
    // Format in UTC+7
    const formatter = new Intl.DateTimeFormat('sv-SE', {
      timeZone: 'Asia/Ho_Chi_Minh',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
    return formatter.format(date).replace(' ', 'T')
  } catch {
    return ''
  }
}

// ─── Form validation ──────────────────────────────────────────────────────────

interface FormErrors {
  title?: string
  startTime?: string
  endTime?: string
  roomId?: string
  hostUserId?: string
  secretaryUserId?: string
  general?: string
}

function validateForm(
  values: FormValues,
  isNew: boolean,
): FormErrors {
  const errors: FormErrors = {}

  if (!values.title.trim()) {
    errors.title = 'Tiêu đề là bắt buộc'
  } else if (values.title.length > 255) {
    errors.title = 'Tiêu đề không được vượt quá 255 ký tự'
  }

  if (!values.startTime) {
    errors.startTime = 'Thời gian bắt đầu là bắt buộc'
  } else if (isNew) {
    const start = new Date(values.startTime + ':00+07:00')
    if (start <= new Date()) {
      errors.startTime = 'Thời gian bắt đầu phải ở tương lai'
    }
  }

  if (!values.endTime) {
    errors.endTime = 'Thời gian kết thúc là bắt buộc'
  } else if (values.startTime && values.endTime) {
    const start = new Date(values.startTime + ':00+07:00')
    const end = new Date(values.endTime + ':00+07:00')
    if (end <= start) {
      errors.endTime = 'Thời gian kết thúc phải sau thời gian bắt đầu'
    }
  }

  if (!values.roomId) {
    errors.roomId = 'Phòng họp là bắt buộc'
  }

  if (!values.hostUserId) {
    errors.hostUserId = 'Chủ trì là bắt buộc'
  }

  if (!values.secretaryUserId) {
    errors.secretaryUserId = 'Thư ký là bắt buộc'
  }

  return errors
}

// ─── Form values ──────────────────────────────────────────────────────────────

interface FormValues {
  title: string
  description: string
  startTime: string  // datetime-local format
  endTime: string    // datetime-local format
  roomId: number | ''
  hostUserId: number | ''
  secretaryUserId: number | ''
  transcriptionPriority: TranscriptionPriority
}

const DEFAULT_VALUES: FormValues = {
  title: '',
  description: '',
  startTime: '',
  endTime: '',
  roomId: '',
  hostUserId: '',
  secretaryUserId: '',
  transcriptionPriority: 'NORMAL_PRIORITY',
}

// ─── Room availability indicator ─────────────────────────────────────────────

interface RoomAvailabilityIndicatorProps {
  roomId: number | ''
  startTime: string
  endTime: string
  excludeMeetingId?: number
}

function RoomAvailabilityIndicator({
  roomId,
  startTime,
  endTime,
  excludeMeetingId,
}: RoomAvailabilityIndicatorProps) {
  const [conflicts, setConflicts] = useState<{ meetingTitle: string }[]>([])
  const [checking, setChecking] = useState(false)

  useEffect(() => {
    if (!roomId || !startTime || !endTime) {
      setConflicts([])
      return
    }

    const start = new Date(startTime + ':00+07:00')
    const end = new Date(endTime + ':00+07:00')
    if (isNaN(start.getTime()) || isNaN(end.getTime()) || end <= start) {
      setConflicts([])
      return
    }

    let cancelled = false
    setChecking(true)

    // Send as local UTC+7 datetime string (no offset) — backend uses LocalDateTime
    getRoomAvailability(
      roomId as number,
      startTime + ':00',
      endTime + ':00',
      excludeMeetingId,
    ).then((slots) => {
      if (!cancelled) {
        setConflicts(slots.map((s) => ({ meetingTitle: s.meetingTitle })))
        setChecking(false)
      }
    })

    return () => { cancelled = true }
  }, [roomId, startTime, endTime, excludeMeetingId])

  if (!roomId || !startTime || !endTime) return null

  if (checking) {
    return (
      <div className="flex items-center gap-1.5 text-body-sm text-on-surface-variant mt-1">
        <div className="w-3 h-3 border-2 border-primary border-t-transparent rounded-full animate-spin" />
        <span>Đang kiểm tra lịch phòng...</span>
      </div>
    )
  }

  if (conflicts.length > 0) {
    return (
      <div
        className="mt-1 bg-error-container text-error rounded-lg px-3 py-2 text-body-sm"
        role="alert"
        data-testid="room-conflict-indicator"
      >
        <div className="font-medium mb-1">⚠ Phòng đã có lịch họp trong khoảng thời gian này:</div>
        <ul className="list-disc list-inside space-y-0.5">
          {conflicts.map((c, i) => (
            <li key={i}>{c.meetingTitle}</li>
          ))}
        </ul>
      </div>
    )
  }

  return (
    <div
      className="flex items-center gap-1.5 text-body-sm text-green-700 mt-1"
      data-testid="room-available-indicator"
    >
      <span className="material-symbols-outlined text-[16px]" aria-hidden="true">
        check_circle
      </span>
      <span>Phòng trống trong khoảng thời gian này</span>
    </div>
  )
}

// ─── MeetingFormPage ──────────────────────────────────────────────────────────

export default function MeetingFormPage() {
  const navigate = useNavigate()
  const { id } = useParams<{ id?: string }>()
  const isEdit = Boolean(id)

  const [values, setValues] = useState<FormValues>(DEFAULT_VALUES)
  const [errors, setErrors] = useState<FormErrors>({})
  const [rooms, setRooms] = useState<Room[]>([])
  const [hostCandidates, setHostCandidates] = useState<MeetingUser[]>([])
  const [secretaryCandidates, setSecretaryCandidates] = useState<MeetingUser[]>([])
  const [loading, setLoading] = useState(false)
  const [initialLoading, setInitialLoading] = useState(isEdit)
  const [conflictError, setConflictError] = useState<string | null>(null)
  const meetingId = id ? Number(id) : undefined

  // Member management state (edit mode only)
  const [members, setMembers] = useState<MeetingMember[]>([])
  const [memberSearch, setMemberSearch] = useState('')
  const [memberSearchResults, setMemberSearchResults] = useState<MeetingUser[]>([])
  const [memberSearchLoading, setMemberSearchLoading] = useState(false)
  const [memberActionLoading, setMemberActionLoading] = useState<number | null>(null)

  // Load rooms and user candidates
  useEffect(() => {
    listRooms()
      .then((res) => setRooms(res.data ?? []))
      .catch(() => {/* non-critical */})

    listHostCandidates()
      .then(setHostCandidates)
      .catch(() => {/* non-critical */})

    listSecretaryCandidates()
      .then(setSecretaryCandidates)
      .catch(() => {/* non-critical */})
  }, [])

  // Load existing meeting data for edit mode
  useEffect(() => {
    if (!isEdit || !id) return

    setInitialLoading(true)
    Promise.all([
      getMeeting(Number(id)),
      listMeetingMembers(Number(id)),
    ])
      .then(([meetingRes, membersRes]) => {
        const m = meetingRes.data
        setValues({
          title: m.title,
          description: m.description ?? '',
          startTime: isoToLocal(m.startTime),
          endTime: isoToLocal(m.endTime),
          roomId: m.room?.id ?? m.roomId ?? '',
          hostUserId: m.hostUser?.id ?? m.hostUserId ?? m.hostId ?? '',
          secretaryUserId: m.secretaryUser?.id ?? m.secretaryUserId ?? m.secretaryId ?? '',
          transcriptionPriority: m.transcriptionPriority,
        })
        setMembers(membersRes.data ?? [])
      })
      .catch(() => {
        setErrors({ general: 'Không thể tải thông tin cuộc họp.' })
      })
      .finally(() => setInitialLoading(false))
  }, [isEdit, id])

  const handleChange = useCallback(
    <K extends keyof FormValues>(field: K, value: FormValues[K]) => {
      setValues((prev) => ({ ...prev, [field]: value }))
      // Clear field error on change
      setErrors((prev) => ({ ...prev, [field]: undefined }))
      setConflictError(null)
    },
    [],
  )

  // Member search with debounce
  useEffect(() => {
    if (!memberSearch.trim()) {
      setMemberSearchResults([])
      return
    }
    const timer = setTimeout(() => {
      setMemberSearchLoading(true)
      searchUsers(memberSearch)
        .then(setMemberSearchResults)
        .catch(() => setMemberSearchResults([]))
        .finally(() => setMemberSearchLoading(false))
    }, 300)
    return () => clearTimeout(timer)
  }, [memberSearch])

  const handleAddMember = async (user: MeetingUser) => {
    if (!meetingId) return
    if (members.some((m) => m.userId === user.id)) return
    setMemberActionLoading(user.id)
    try {
      await addMeetingMember(meetingId, user.id)
      const res = await listMeetingMembers(meetingId)
      setMembers(res.data ?? [])
      setMemberSearch('')
      setMemberSearchResults([])
    } catch {
      // non-critical, user may already be a member
    } finally {
      setMemberActionLoading(null)
    }
  }

  const handleRemoveMember = async (userId: number) => {
    if (!meetingId) return
    setMemberActionLoading(userId)
    try {
      await removeMeetingMember(meetingId, userId)
      setMembers((prev) => prev.filter((m) => m.id !== userId))
    } catch {
      // non-critical
    } finally {
      setMemberActionLoading(null)
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()

    const validationErrors = validateForm(values, !isEdit)
    if (Object.keys(validationErrors).length > 0) {
      setErrors(validationErrors)
      return
    }

    setLoading(true)
    setConflictError(null)

    try {
      const payload = {
        title: values.title.trim(),
        description: values.description.trim() || undefined,
        startTime: localToIso(values.startTime),
        endTime: localToIso(values.endTime),
        roomId: values.roomId as number,
        hostUserId: values.hostUserId as number,
        secretaryUserId: values.secretaryUserId as number,
        transcriptionPriority: values.transcriptionPriority,
      }

      if (isEdit && id) {
        await updateMeeting(Number(id), payload)
        navigate(`/meetings/${id}`)
      } else {
        const res = await createMeeting(payload)
        navigate(`/meetings/${res.data.id}`)
      }
    } catch (err) {
      if (isSchedulingConflict(err)) {
        setConflictError(getConflictMessage(err))
      } else {
        setErrors({ general: 'Đã xảy ra lỗi. Vui lòng thử lại.' })
      }
    } finally {
      setLoading(false)
    }
  }

  if (initialLoading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* Header */}
      <div>
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-1 text-body-sm text-on-surface-variant hover:text-on-surface mb-3"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
            arrow_back
          </span>
          Quay lại
        </button>
        <h1 className="text-h3 font-semibold text-on-surface">
          {isEdit ? 'Chỉnh sửa cuộc họp' : 'Tạo cuộc họp mới'}
        </h1>
      </div>

      {/* General error */}
      {errors.general && (
        <div
          className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm"
          role="alert"
        >
          {errors.general}
        </div>
      )}

      {/* Conflict error */}
      {conflictError && (
        <div
          className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm"
          role="alert"
          data-testid="conflict-error"
        >
          <div className="font-medium">⚠ Xung đột lịch phòng họp</div>
          <div className="mt-1">{conflictError}</div>
        </div>
      )}

      {/* Form */}
      <form
        onSubmit={handleSubmit}
        noValidate
        className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 space-y-5"
      >
        {/* Title */}
        <div>
          <label
            htmlFor="title"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Tiêu đề <span className="text-error">*</span>
          </label>
          <input
            id="title"
            type="text"
            value={values.title}
            onChange={(e) => handleChange('title', e.target.value)}
            maxLength={255}
            placeholder="Nhập tiêu đề cuộc họp"
            aria-describedby={errors.title ? 'title-error' : undefined}
            aria-invalid={Boolean(errors.title)}
            className={`w-full border rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface
                        focus:outline-none focus:ring-2 focus:ring-primary
                        ${errors.title ? 'border-error' : 'border-outline-variant'}`}
          />
          {errors.title && (
            <p id="title-error" className="mt-1 text-body-sm text-error" role="alert">
              {errors.title}
            </p>
          )}
        </div>

        {/* Description */}
        <div>
          <label
            htmlFor="description"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Mô tả
          </label>
          <textarea
            id="description"
            value={values.description}
            onChange={(e) => handleChange('description', e.target.value)}
            rows={3}
            placeholder="Mô tả ngắn về cuộc họp (tùy chọn)"
            className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                       text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary
                       resize-none"
          />
        </div>

        {/* Start time */}
        <div>
          <label
            htmlFor="startTime"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Thời gian bắt đầu <span className="text-error">*</span>
          </label>
          <input
            id="startTime"
            type="datetime-local"
            value={values.startTime}
            onChange={(e) => handleChange('startTime', e.target.value)}
            aria-describedby={errors.startTime ? 'startTime-error' : undefined}
            aria-invalid={Boolean(errors.startTime)}
            className={`w-full border rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface
                        focus:outline-none focus:ring-2 focus:ring-primary
                        ${errors.startTime ? 'border-error' : 'border-outline-variant'}`}
          />
          {errors.startTime && (
            <p id="startTime-error" className="mt-1 text-body-sm text-error" role="alert">
              {errors.startTime}
            </p>
          )}
        </div>

        {/* End time */}
        <div>
          <label
            htmlFor="endTime"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Thời gian kết thúc <span className="text-error">*</span>
          </label>
          <input
            id="endTime"
            type="datetime-local"
            value={values.endTime}
            onChange={(e) => handleChange('endTime', e.target.value)}
            aria-describedby={errors.endTime ? 'endTime-error' : undefined}
            aria-invalid={Boolean(errors.endTime)}
            className={`w-full border rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface
                        focus:outline-none focus:ring-2 focus:ring-primary
                        ${errors.endTime ? 'border-error' : 'border-outline-variant'}`}
          />
          {errors.endTime && (
            <p id="endTime-error" className="mt-1 text-body-sm text-error" role="alert">
              {errors.endTime}
            </p>
          )}
        </div>

        {/* Room */}
        <div>
          <label
            htmlFor="roomId"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Phòng họp <span className="text-error">*</span>
          </label>
          <select
            id="roomId"
            value={values.roomId}
            onChange={(e) =>
              handleChange('roomId', e.target.value === '' ? '' : Number(e.target.value))
            }
            aria-describedby={errors.roomId ? 'roomId-error' : undefined}
            aria-invalid={Boolean(errors.roomId)}
            className={`w-full border rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface
                        focus:outline-none focus:ring-2 focus:ring-primary
                        ${errors.roomId ? 'border-error' : 'border-outline-variant'}`}
          >
            <option value="">-- Chọn phòng họp --</option>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.name}
                {r.capacity ? ` (sức chứa: ${r.capacity})` : ''}
                {r.department ? ` — ${r.department.name}` : ''}
              </option>
            ))}
          </select>
          {errors.roomId && (
            <p id="roomId-error" className="mt-1 text-body-sm text-error" role="alert">
              {errors.roomId}
            </p>
          )}

          {/* Room availability indicator */}
          <RoomAvailabilityIndicator
            roomId={values.roomId}
            startTime={values.startTime}
            endTime={values.endTime}
            excludeMeetingId={meetingId}
          />
        </div>

        {/* Host */}
        <div>
          <label
            htmlFor="hostUserId"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Chủ trì <span className="text-error">*</span>
            <span className="ml-1 text-on-surface-variant font-normal">(ADMIN hoặc SECRETARY)</span>
          </label>
          <select
            id="hostUserId"
            value={values.hostUserId}
            onChange={(e) =>
              handleChange('hostUserId', e.target.value === '' ? '' : Number(e.target.value))
            }
            aria-describedby={errors.hostUserId ? 'hostUserId-error' : undefined}
            aria-invalid={Boolean(errors.hostUserId)}
            data-testid="host-select"
            className={`w-full border rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface
                        focus:outline-none focus:ring-2 focus:ring-primary
                        ${errors.hostUserId ? 'border-error' : 'border-outline-variant'}`}
          >
            <option value="">-- Chọn chủ trì --</option>
            {hostCandidates.map((u) => (
              <option key={u.id} value={u.id} data-role={u.role}>
                {formatUserLabel(u)}
              </option>
            ))}
          </select>
          {errors.hostUserId && (
            <p id="hostUserId-error" className="mt-1 text-body-sm text-error" role="alert">
              {errors.hostUserId}
            </p>
          )}
        </div>

        {/* Secretary */}
        <div>
          <label
            htmlFor="secretaryUserId"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Thư ký <span className="text-error">*</span>
            <span className="ml-1 text-on-surface-variant font-normal">(SECRETARY)</span>
          </label>
          <select
            id="secretaryUserId"
            value={values.secretaryUserId}
            onChange={(e) =>
              handleChange('secretaryUserId', e.target.value === '' ? '' : Number(e.target.value))
            }
            aria-describedby={errors.secretaryUserId ? 'secretaryUserId-error' : undefined}
            aria-invalid={Boolean(errors.secretaryUserId)}
            data-testid="secretary-select"
            className={`w-full border rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface
                        focus:outline-none focus:ring-2 focus:ring-primary
                        ${errors.secretaryUserId ? 'border-error' : 'border-outline-variant'}`}
          >
            <option value="">-- Chọn thư ký --</option>
            {secretaryCandidates.map((u) => (
              <option key={u.id} value={u.id} data-role={u.role}>
                {formatUserLabel(u)}
              </option>
            ))}
          </select>
          {errors.secretaryUserId && (
            <p id="secretaryUserId-error" className="mt-1 text-body-sm text-error" role="alert">
              {errors.secretaryUserId}
            </p>
          )}
        </div>

        {/* Transcription priority */}
        <div>
          <label
            htmlFor="transcriptionPriority"
            className="block text-label-md text-on-surface-variant mb-1"
          >
            Ưu tiên phiên âm
          </label>
          <select
            id="transcriptionPriority"
            value={values.transcriptionPriority}
            onChange={(e) =>
              handleChange('transcriptionPriority', e.target.value as TranscriptionPriority)
            }
            className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                       text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
          >
            <option value="NORMAL_PRIORITY">Bình thường</option>
            <option value="HIGH_PRIORITY">Cao (phiên âm thời gian thực)</option>
          </select>
        </div>

        {/* Member management — edit mode only */}
        {isEdit && (
          <div className="border-t border-outline-variant pt-5 space-y-3">
            <h2 className="text-label-lg font-semibold text-on-surface">Thành viên cuộc họp</h2>

            {/* Current members */}
            {members.length > 0 ? (
              <ul className="space-y-1.5">
                {members.map((m) => (
                  <li
                    key={m.id}
                    className="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-surface-container"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <div className="w-7 h-7 rounded-full bg-primary/10 flex items-center justify-center shrink-0">
                        <span className="text-primary text-label-sm font-semibold">
                          {m.fullName?.charAt(0).toUpperCase() ?? '?'}
                        </span>
                      </div>
                      <div className="min-w-0">
                        <div className="text-body-sm font-medium text-on-surface truncate">
                          {formatUserLabel({ id: m.id, fullName: m.fullName, departmentName: m.department?.name })}
                        </div>
                        <div className="text-label-sm text-on-surface-variant">{m.username}</div>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => handleRemoveMember(m.id)}
                      disabled={memberActionLoading === m.id}
                      className="p-1 rounded-lg hover:bg-error-container text-on-surface-variant hover:text-error transition-colors shrink-0 disabled:opacity-50"
                      aria-label={`Xóa ${m.fullName} khỏi cuộc họp`}
                    >
                      {memberActionLoading === m.id
                        ? <div className="w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin" />
                        : <span className="material-symbols-outlined text-[18px]">person_remove</span>
                      }
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-body-sm text-on-surface-variant">Chưa có thành viên nào.</p>
            )}

            {/* Search to add member */}
            <div className="relative">
              <input
                type="text"
                value={memberSearch}
                onChange={(e) => setMemberSearch(e.target.value)}
                placeholder="Tìm kiếm thành viên theo tên hoặc username..."
                className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                           text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
              />
              {memberSearchLoading && (
                <div className="absolute right-3 top-1/2 -translate-y-1/2">
                  <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />
                </div>
              )}
              {memberSearchResults.length > 0 && (
                <ul className="absolute z-10 mt-1 w-full bg-surface border border-outline-variant rounded-lg shadow-lg max-h-48 overflow-y-auto">
                  {memberSearchResults.map((u) => {
                    const alreadyMember = members.some((m) => m.userId === u.id)
                    return (
                      <li key={u.id}>
                        <button
                          type="button"
                          onClick={() => !alreadyMember && handleAddMember(u)}
                          disabled={alreadyMember || memberActionLoading === u.id}
                          className={`w-full text-left px-3 py-2 text-body-sm flex items-center justify-between gap-2
                                      ${alreadyMember
                                        ? 'text-on-surface-variant cursor-default'
                                        : 'hover:bg-surface-container text-on-surface'}`}
                        >
                          <span>{formatUserLabel(u)}</span>
                          {alreadyMember
                            ? <span className="text-label-sm text-on-surface-variant">Đã thêm</span>
                            : <span className="material-symbols-outlined text-[16px] text-primary">person_add</span>
                          }
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </div>
          </div>
        )}

        {/* Actions */}
        <div className="flex items-center justify-end gap-3 pt-2 border-t border-outline-variant">
          <button
            type="button"
            onClick={() => navigate(-1)}
            className="px-4 py-2 rounded-xl text-button font-medium text-on-surface
                       border border-outline-variant hover:bg-surface-container transition-colors"
          >
            Hủy
          </button>
          <button
            type="submit"
            disabled={loading}
            className="px-4 py-2 rounded-xl text-button font-medium bg-primary text-white
                       hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed
                       transition-colors flex items-center gap-2"
          >
            {loading && (
              <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
            )}
            {isEdit ? 'Lưu thay đổi' : 'Tạo cuộc họp'}
          </button>
        </div>
      </form>
    </div>
  )
}
