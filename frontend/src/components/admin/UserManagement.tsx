/**
 * UserManagement — list, create, edit, delete users and reset passwords.
 * Requirements: 11.3, 11.8
 */

import { useEffect, useState, useCallback } from 'react'
import {
  listUsers,
  createUser,
  updateUser,
  deleteUser,
  resetPassword,
} from '../../services/userService'
import { listDepartments } from '../../services/meetingService'
import type { CreateUserRequest, UpdateUserRequest } from '../../services/userService'
import type { MeetingUser, Department } from '../../types/meeting'
import type { PageResponse } from '../../types/api'

// ─── Role badge ───────────────────────────────────────────────────────────────

const ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Quản trị viên',
  SECRETARY: 'Thư ký',
  USER: 'Người dùng',
}

const ROLE_CLASSES: Record<string, string> = {
  ADMIN: 'bg-purple-100 text-purple-700',
  SECRETARY: 'bg-blue-100 text-blue-700',
  USER: 'bg-slate-100 text-slate-600',
}

function RoleBadge({ role }: { role: string }) {
  return (
    <span
      data-testid={`role-badge-${role}`}
      className={`inline-flex items-center px-2 py-0.5 rounded text-label-md font-semibold ${ROLE_CLASSES[role] ?? 'bg-slate-100 text-slate-600'}`}
    >
      {ROLE_LABELS[role] ?? role}
    </span>
  )
}

// ─── Create user modal ────────────────────────────────────────────────────────

interface CreateUserModalProps {
  onClose: () => void
  onSuccess: () => void
}

function CreateUserModal({ onClose, onSuccess }: CreateUserModalProps) {
  const [form, setForm] = useState<CreateUserRequest>({
    username: '',
    email: '',
    fullName: '',
    password: '',
    role: 'USER',
    departmentId: undefined,
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])

  useEffect(() => {
    listDepartments().then((res) => setDepartments(res.data ?? [])).catch(() => {})
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await createUser(form)
      onSuccess()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg ?? 'Không thể tạo người dùng. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      data-testid="create-user-modal"
      role="dialog"
      aria-modal="true"
      aria-label="Tạo người dùng mới"
    >
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-md shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-4">Tạo người dùng mới</h2>
        {error && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm mb-4" role="alert" data-testid="create-user-error">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Tên đăng nhập <span className="text-error">*</span>
            </label>
            <input
              type="text"
              required
              value={form.username}
              onChange={(e) => setForm((f) => ({ ...f, username: e.target.value }))}
              data-testid="create-username-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Email <span className="text-error">*</span>
            </label>
            <input
              type="email"
              required
              value={form.email}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              data-testid="create-email-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Họ và tên <span className="text-error">*</span>
            </label>
            <input
              type="text"
              required
              value={form.fullName}
              onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
              data-testid="create-fullname-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Mật khẩu <span className="text-error">*</span>
            </label>
            <input
              type="password"
              required
              minLength={8}
              value={form.password}
              onChange={(e) => setForm((f) => ({ ...f, password: e.target.value }))}
              data-testid="create-password-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
            <p className="text-label-md text-on-surface-variant mt-1">Tối thiểu 8 ký tự</p>
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Vai trò</label>
            <select
              value={form.role}
              onChange={(e) => setForm((f) => ({ ...f, role: e.target.value as CreateUserRequest['role'] }))}
              data-testid="create-role-select"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="USER">Người dùng</option>
              <option value="SECRETARY">Thư ký</option>
              <option value="ADMIN">Quản trị viên</option>
            </select>
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Phòng ban</label>
            <select
              value={form.departmentId ?? ''}
              onChange={(e) => setForm((f) => ({ ...f, departmentId: e.target.value ? Number(e.target.value) : undefined }))}
              data-testid="create-department-select"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">— Không có phòng ban —</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-button font-medium text-on-surface border border-outline-variant hover:bg-surface-container transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading}
              data-testid="create-user-submit"
              className="px-4 py-2 rounded-xl text-button font-medium bg-primary text-white hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
            >
              {loading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
              Tạo người dùng
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Edit user modal ──────────────────────────────────────────────────────────

interface EditUserModalProps {
  user: MeetingUser
  onClose: () => void
  onSuccess: () => void
}

function EditUserModal({ user, onClose, onSuccess }: EditUserModalProps) {
  const [form, setForm] = useState<UpdateUserRequest>({
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    departmentId: user.department?.id,
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [departments, setDepartments] = useState<Department[]>([])

  useEffect(() => {
    listDepartments().then((res) => setDepartments(res.data ?? [])).catch(() => {})
  }, [])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      await updateUser(user.id, form)
      onSuccess()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg ?? 'Không thể cập nhật người dùng. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      data-testid="edit-user-modal"
      role="dialog"
      aria-modal="true"
      aria-label="Chỉnh sửa người dùng"
    >
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-md shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-4">
          Chỉnh sửa: {user.fullName}
        </h2>
        {error && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm mb-4" role="alert" data-testid="edit-user-error">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Email</label>
            <input
              type="email"
              value={form.email ?? ''}
              onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              data-testid="edit-email-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Họ và tên</label>
            <input
              type="text"
              value={form.fullName ?? ''}
              onChange={(e) => setForm((f) => ({ ...f, fullName: e.target.value }))}
              data-testid="edit-fullname-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Vai trò</label>
            <select
              value={form.role ?? 'USER'}
              onChange={(e) => setForm((f) => ({ ...f, role: e.target.value as UpdateUserRequest['role'] }))}
              data-testid="edit-role-select"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="USER">Người dùng</option>
              <option value="SECRETARY">Thư ký</option>
              <option value="ADMIN">Quản trị viên</option>
            </select>
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Phòng ban</label>
            <select
              value={form.departmentId ?? ''}
              onChange={(e) => setForm((f) => ({ ...f, departmentId: e.target.value ? Number(e.target.value) : undefined }))}
              data-testid="edit-department-select"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">— Không có phòng ban —</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 rounded-xl text-button font-medium text-on-surface border border-outline-variant hover:bg-surface-container transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading}
              data-testid="edit-user-submit"
              className="px-4 py-2 rounded-xl text-button font-medium bg-primary text-white hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
            >
              {loading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
              Lưu thay đổi
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Delete confirmation dialog ───────────────────────────────────────────────

interface DeleteConfirmDialogProps {
  user: MeetingUser
  onClose: () => void
  onConfirm: () => void
  loading: boolean
}

function DeleteConfirmDialog({ user, onClose, onConfirm, loading }: DeleteConfirmDialogProps) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      data-testid="delete-confirm-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="Xác nhận xóa người dùng"
    >
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-sm shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-2">Xóa người dùng</h2>
        <p className="text-body-sm text-on-surface-variant mb-6">
          Bạn có chắc muốn xóa người dùng <strong>{user.fullName}</strong>? Hành động này không thể hoàn tác.
        </p>
        <div className="flex justify-end gap-3">
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
            onClick={onConfirm}
            disabled={loading}
            data-testid="delete-confirm-btn"
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

// ─── Reset password dialog ────────────────────────────────────────────────────

interface ResetPasswordDialogProps {
  user: MeetingUser
  onClose: () => void
  onSuccess: () => void
}

function ResetPasswordDialog({ user, onClose, onSuccess }: ResetPasswordDialogProps) {
  const [newPassword, setNewPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!newPassword.trim()) return
    setLoading(true)
    setError(null)
    try {
      await resetPassword(user.id, newPassword)
      setSuccess(true)
      setTimeout(() => {
        onSuccess()
      }, 1200)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg ?? 'Không thể đặt lại mật khẩu. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      data-testid="reset-password-dialog"
      role="dialog"
      aria-modal="true"
      aria-label="Đặt lại mật khẩu"
    >
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-sm shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-2">Đặt lại mật khẩu</h2>
        <p className="text-body-sm text-on-surface-variant mb-4">
          Đặt mật khẩu mới cho <strong>{user.fullName}</strong>
        </p>
        {error && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm mb-4" role="alert" data-testid="reset-password-error">
            {error}
          </div>
        )}
        {success && (
          <div className="bg-green-100 text-green-700 rounded-lg px-3 py-2 text-body-sm mb-4" data-testid="reset-password-success">
            Đặt lại mật khẩu thành công!
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Mật khẩu mới <span className="text-error">*</span>
            </label>
            <input
              type="password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              data-testid="reset-password-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant">
            <button
              type="button"
              onClick={onClose}
              disabled={loading}
              className="px-4 py-2 rounded-xl text-button font-medium text-on-surface border border-outline-variant hover:bg-surface-container transition-colors"
            >
              Hủy
            </button>
            <button
              type="submit"
              disabled={loading || success}
              data-testid="reset-password-submit"
              className="px-4 py-2 rounded-xl text-button font-medium bg-primary text-white hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
            >
              {loading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
              Đặt lại mật khẩu
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Page size options ────────────────────────────────────────────────────────

const PAGE_SIZE_OPTIONS = [10, 20, 50]

// ─── UserManagement ───────────────────────────────────────────────────────────

export default function UserManagement() {
  const [users, setUsers] = useState<MeetingUser[]>([])
  const [pageInfo, setPageInfo] = useState<Omit<PageResponse<MeetingUser>, 'content'>>({
    totalElements: 0,
    totalPages: 0,
    size: 10,
    number: 0,
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Filters
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<'ADMIN' | 'SECRETARY' | 'USER' | ''>('')
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  // Modal state
  const [showCreate, setShowCreate] = useState(false)
  const [editUser, setEditUser] = useState<MeetingUser | null>(null)
  const [deleteUser_, setDeleteUser] = useState<MeetingUser | null>(null)
  const [deleteLoading, setDeleteLoading] = useState(false)
  const [resetUser, setResetUser] = useState<MeetingUser | null>(null)

  const fetchUsers = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await listUsers({
        page,
        size: pageSize,
        search: search || undefined,
        role: roleFilter || undefined,
      })
      const pageData = res.data
      setUsers(pageData?.content ?? [])
      setPageInfo({
        totalElements: pageData?.totalElements ?? 0,
        totalPages: pageData?.totalPages ?? 0,
        size: pageData?.size ?? pageSize,
        number: pageData?.number ?? 0,
      })
    } catch {
      setError('Không thể tải danh sách người dùng. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, search, roleFilter])

  useEffect(() => {
    fetchUsers()
  }, [fetchUsers])

  const handleDeleteConfirm = async () => {
    if (!deleteUser_) return
    setDeleteLoading(true)
    try {
      await deleteUser(deleteUser_.id)
      setDeleteUser(null)
      fetchUsers()
    } catch {
      setError('Không thể xóa người dùng. Vui lòng thử lại.')
      setDeleteUser(null)
    } finally {
      setDeleteLoading(false)
    }
  }

  return (
    <div className="space-y-6" data-testid="user-management">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-h3 font-semibold text-on-surface">Quản lý người dùng</h2>
          <p className="text-body-sm text-on-surface-variant mt-1">
            {pageInfo.totalElements > 0 ? `${pageInfo.totalElements} người dùng` : 'Không có người dùng nào'}
          </p>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          data-testid="create-user-btn"
          className="inline-flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-primary/90 transition-colors"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">person_add</span>
          Thêm người dùng
        </button>
      </div>

      {/* Filters */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-4">
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Tìm kiếm</label>
            <input
              type="text"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(0) }}
              placeholder="Tên, email, tên đăng nhập..."
              data-testid="user-search-input"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Vai trò</label>
            <select
              value={roleFilter}
              onChange={(e) => { setRoleFilter(e.target.value as typeof roleFilter); setPage(0) }}
              data-testid="role-filter-select"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Tất cả vai trò</option>
              <option value="ADMIN">Quản trị viên</option>
              <option value="SECRETARY">Thư ký</option>
              <option value="USER">Người dùng</option>
            </select>
          </div>
        </div>
      </div>

      {/* Error */}
      {error && (
        <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert" data-testid="user-management-error">
          {error}
        </div>
      )}

      {/* Table */}
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          </div>
        ) : users.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 text-on-surface-variant" data-testid="user-empty-state">
            <span className="material-symbols-outlined text-5xl mb-3" aria-hidden="true">group_off</span>
            <p className="text-body-md">Không có người dùng nào phù hợp</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-body-sm" data-testid="user-table">
              <thead>
                <tr className="border-b border-outline-variant bg-surface-container-low">
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">Họ và tên</th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden md:table-cell">Email</th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">Vai trò</th>
                  <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden lg:table-cell">Phòng ban</th>
                  <th className="px-4 py-3" aria-label="Hành động" />
                </tr>
              </thead>
              <tbody>
                {users.map((u, idx) => (
                  <tr
                    key={u.id}
                    data-testid={`user-row-${u.id}`}
                    className={`border-b border-outline-variant last:border-0 hover:bg-surface-container-low transition-colors ${idx % 2 === 0 ? '' : 'bg-surface-container/30'}`}
                  >
                    <td className="px-4 py-3">
                      <div className="font-medium text-on-surface">{u.fullName}</div>
                      <div className="text-label-md text-on-surface-variant">{u.username}</div>
                    </td>
                    <td className="px-4 py-3 text-on-surface-variant hidden md:table-cell">{u.email}</td>
                    <td className="px-4 py-3"><RoleBadge role={u.role} /></td>
                    <td className="px-4 py-3 text-on-surface-variant hidden lg:table-cell">{u.departmentName ?? u.department?.name ?? '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex items-center justify-end gap-2">
                        <button
                          onClick={() => setEditUser(u)}
                          data-testid={`edit-user-btn-${u.id}`}
                          aria-label={`Chỉnh sửa ${u.fullName}`}
                          className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-on-surface transition-colors"
                        >
                          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">edit</span>
                        </button>
                        <button
                          onClick={() => setResetUser(u)}
                          data-testid={`reset-password-btn-${u.id}`}
                          aria-label={`Đặt lại mật khẩu ${u.fullName}`}
                          className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-on-surface transition-colors"
                        >
                          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">lock_reset</span>
                        </button>
                        <button
                          onClick={() => setDeleteUser(u)}
                          data-testid={`delete-user-btn-${u.id}`}
                          aria-label={`Xóa ${u.fullName}`}
                          className="p-1.5 rounded-lg hover:bg-error-container text-on-surface-variant hover:text-error transition-colors"
                        >
                          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">delete</span>
                        </button>
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
      {pageInfo.totalPages > 1 && (
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-body-sm text-on-surface-variant">
            <span>Hiển thị</span>
            <select
              value={pageSize}
              onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0) }}
              className="border border-outline-variant rounded-lg px-2 py-1 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              {PAGE_SIZE_OPTIONS.map((s) => (
                <option key={s} value={s}>{s}</option>
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
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">first_page</span>
            </button>
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="p-2 rounded-lg hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Trang trước"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">chevron_left</span>
            </button>
            {Array.from({ length: Math.min(5, pageInfo.totalPages) }, (_, i) => {
              const startPage = Math.max(0, Math.min(page - 2, pageInfo.totalPages - 5))
              const pageNum = startPage + i
              return (
                <button
                  key={pageNum}
                  onClick={() => setPage(pageNum)}
                  className={`w-9 h-9 rounded-lg text-body-sm font-medium transition-colors ${pageNum === page ? 'bg-primary text-white' : 'hover:bg-surface-container text-on-surface'}`}
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
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">chevron_right</span>
            </button>
            <button
              onClick={() => setPage(pageInfo.totalPages - 1)}
              disabled={page >= pageInfo.totalPages - 1}
              className="p-2 rounded-lg hover:bg-surface-container disabled:opacity-40 disabled:cursor-not-allowed"
              aria-label="Trang cuối"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">last_page</span>
            </button>
          </div>
          <div className="text-body-sm text-on-surface-variant">
            Trang {page + 1} / {pageInfo.totalPages}
          </div>
        </div>
      )}

      {/* Modals */}
      {showCreate && (
        <CreateUserModal
          onClose={() => setShowCreate(false)}
          onSuccess={() => { setShowCreate(false); fetchUsers() }}
        />
      )}
      {editUser && (
        <EditUserModal
          user={editUser}
          onClose={() => setEditUser(null)}
          onSuccess={() => { setEditUser(null); fetchUsers() }}
        />
      )}
      {deleteUser_ && (
        <DeleteConfirmDialog
          user={deleteUser_}
          onClose={() => setDeleteUser(null)}
          onConfirm={handleDeleteConfirm}
          loading={deleteLoading}
        />
      )}
      {resetUser && (
        <ResetPasswordDialog
          user={resetUser}
          onClose={() => setResetUser(null)}
          onSuccess={() => setResetUser(null)}
        />
      )}
    </div>
  )
}
