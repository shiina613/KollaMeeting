/**
 * StatusBadge — reusable status badge for meetings, recordings, etc.
 * Requirements: 1.4
 */

import type { MeetingStatus, RecordingStatus } from '../../types/meeting'

// ─── Meeting status ───────────────────────────────────────────────────────────

const MEETING_STATUS_LABELS: Record<MeetingStatus, string> = {
  SCHEDULED: 'Đã lên lịch',
  ACTIVE: 'Đang diễn ra',
  ENDED: 'Đã kết thúc',
}

const MEETING_STATUS_CLASSES: Record<MeetingStatus, string> = {
  SCHEDULED: 'bg-blue-100 text-blue-700',
  ACTIVE: 'bg-green-100 text-green-700',
  ENDED: 'bg-slate-100 text-slate-600',
}

// ─── Recording status ─────────────────────────────────────────────────────────

const RECORDING_STATUS_LABELS: Record<RecordingStatus, string> = {
  RECORDING: 'Đang ghi',
  COMPLETED: 'Hoàn thành',
  FAILED: 'Thất bại',
}

const RECORDING_STATUS_CLASSES: Record<RecordingStatus, string> = {
  RECORDING: 'bg-primary/10 text-primary',
  COMPLETED: 'bg-green-100 text-green-700',
  FAILED: 'bg-error-container text-error',
}

// ─── Generic badge ────────────────────────────────────────────────────────────

interface GenericBadgeProps {
  label: string
  className: string
  testId?: string
}

function Badge({ label, className, testId }: GenericBadgeProps) {
  return (
    <span
      data-testid={testId}
      className={`inline-flex items-center px-2 py-0.5 rounded text-label-md font-semibold ${className}`}
    >
      {label}
    </span>
  )
}

// ─── MeetingStatusBadge ───────────────────────────────────────────────────────

interface MeetingStatusBadgeProps {
  status: MeetingStatus
  testId?: string
}

export function MeetingStatusBadge({ status, testId }: MeetingStatusBadgeProps) {
  return (
    <Badge
      label={MEETING_STATUS_LABELS[status]}
      className={MEETING_STATUS_CLASSES[status]}
      testId={testId ?? `meeting-status-${status}`}
    />
  )
}

// ─── RecordingStatusBadge ─────────────────────────────────────────────────────

interface RecordingStatusBadgeProps {
  status: RecordingStatus
  testId?: string
}

export function RecordingStatusBadge({ status, testId }: RecordingStatusBadgeProps) {
  return (
    <Badge
      label={RECORDING_STATUS_LABELS[status]}
      className={RECORDING_STATUS_CLASSES[status]}
      testId={testId ?? `recording-status-${status}`}
    />
  )
}

// ─── RoleBadge ────────────────────────────────────────────────────────────────

type UserRole = 'ADMIN' | 'SECRETARY' | 'USER'

const ROLE_LABELS: Record<UserRole, string> = {
  ADMIN: 'Quản trị viên',
  SECRETARY: 'Thư ký',
  USER: 'Người dùng',
}

const ROLE_CLASSES: Record<UserRole, string> = {
  ADMIN: 'bg-purple-100 text-purple-700',
  SECRETARY: 'bg-blue-100 text-blue-700',
  USER: 'bg-slate-100 text-slate-600',
}

interface RoleBadgeProps {
  role: UserRole
  testId?: string
}

export function RoleBadge({ role, testId }: RoleBadgeProps) {
  return (
    <Badge
      label={ROLE_LABELS[role]}
      className={ROLE_CLASSES[role]}
      testId={testId ?? `role-badge-${role}`}
    />
  )
}

// ─── Default export (MeetingStatusBadge for backward compat) ─────────────────

export default MeetingStatusBadge
