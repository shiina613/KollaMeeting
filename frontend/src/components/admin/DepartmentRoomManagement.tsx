/**
 * DepartmentRoomManagement — CRUD for departments and rooms.
 * Requirements: 12.1–12.8
 */

import { useEffect, useState, useCallback } from 'react'
import {
  listDepartments,
  createDepartment,
  updateDepartment,
  deleteDepartment,
  type DepartmentDto,
  type CreateDepartmentRequest,
  type UpdateDepartmentRequest,
} from '../../services/departmentService'
import {
  listRooms,
  createRoom,
  updateRoom,
  deleteRoom,
  type RoomDto,
  type CreateRoomRequest,
  type UpdateRoomRequest,
} from '../../services/roomService'

// ─── Department modals ────────────────────────────────────────────────────────

function DepartmentFormModal({
  dept,
  onClose,
  onSuccess,
}: {
  dept?: DepartmentDto
  onClose: () => void
  onSuccess: () => void
}) {
  const isEdit = !!dept
  const [name, setName] = useState(dept?.name ?? '')
  const [description, setDescription] = useState(dept?.description ?? '')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      if (isEdit) {
        await updateDepartment(dept!.id, { name, description: description || undefined })
      } else {
        await createDepartment({ name, description: description || undefined })
      }
      onSuccess()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg ?? 'Không thể lưu phòng ban.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-md shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-4">
          {isEdit ? 'Chỉnh sửa phòng ban' : 'Thêm phòng ban'}
        </h2>
        {error && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm mb-4" role="alert">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Tên phòng ban <span className="text-error">*</span>
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Mô tả</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary resize-none"
            />
          </div>
          <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant">
            <button type="button" onClick={onClose} className="px-4 py-2 rounded-xl text-button font-medium text-on-surface border border-outline-variant hover:bg-surface-container transition-colors">
              Hủy
            </button>
            <button type="submit" disabled={loading} className="px-4 py-2 rounded-xl text-button font-medium bg-primary text-white hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-2">
              {loading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
              {isEdit ? 'Lưu thay đổi' : 'Thêm phòng ban'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Room modals ──────────────────────────────────────────────────────────────

function RoomFormModal({
  room,
  departments,
  onClose,
  onSuccess,
}: {
  room?: RoomDto
  departments: DepartmentDto[]
  onClose: () => void
  onSuccess: () => void
}) {
  const isEdit = !!room
  const [name, setName] = useState(room?.name ?? '')
  const [capacity, setCapacity] = useState(room?.capacity?.toString() ?? '')
  const [departmentId, setDepartmentId] = useState<number | ''>(room?.departmentId ?? (departments[0]?.id ?? ''))
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (departmentId === '') { setError('Vui lòng chọn phòng ban.'); return }
    setLoading(true)
    setError(null)
    try {
      if (isEdit) {
        const data: UpdateRoomRequest = {
          name,
          capacity: capacity ? Number(capacity) : undefined,
          departmentId: departmentId as number,
        }
        await updateRoom(room!.id, data)
      } else {
        const data: CreateRoomRequest = {
          name,
          capacity: capacity ? Number(capacity) : undefined,
          departmentId: departmentId as number,
        }
        await createRoom(data)
      }
      onSuccess()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setError(msg ?? 'Không thể lưu phòng họp.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-md shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-4">
          {isEdit ? 'Chỉnh sửa phòng họp' : 'Thêm phòng họp'}
        </h2>
        {error && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm mb-4" role="alert">
            {error}
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Tên phòng họp <span className="text-error">*</span>
            </label>
            <input
              type="text"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">Sức chứa</label>
            <input
              type="number"
              min={1}
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              placeholder="Không giới hạn"
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            />
          </div>
          <div>
            <label className="block text-label-md text-on-surface-variant mb-1">
              Phòng ban <span className="text-error">*</span>
            </label>
            <select
              required
              value={departmentId}
              onChange={(e) => setDepartmentId(e.target.value === '' ? '' : Number(e.target.value))}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
            >
              <option value="">Chọn phòng ban</option>
              {departments.map((d) => (
                <option key={d.id} value={d.id}>{d.name}</option>
              ))}
            </select>
          </div>
          <div className="flex justify-end gap-3 pt-2 border-t border-outline-variant">
            <button type="button" onClick={onClose} className="px-4 py-2 rounded-xl text-button font-medium text-on-surface border border-outline-variant hover:bg-surface-container transition-colors">
              Hủy
            </button>
            <button type="submit" disabled={loading} className="px-4 py-2 rounded-xl text-button font-medium bg-primary text-white hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-2">
              {loading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
              {isEdit ? 'Lưu thay đổi' : 'Thêm phòng họp'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

// ─── Delete confirm ───────────────────────────────────────────────────────────

function DeleteConfirm({
  name,
  onClose,
  onConfirm,
  loading,
}: {
  name: string
  onClose: () => void
  onConfirm: () => void
  loading: boolean
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40" role="dialog" aria-modal="true">
      <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 w-full max-w-sm shadow-xl">
        <h2 className="text-h3 font-semibold text-on-surface mb-2">Xác nhận xóa</h2>
        <p className="text-body-sm text-on-surface-variant mb-6">
          Bạn có chắc muốn xóa <strong>{name}</strong>? Hành động này không thể hoàn tác.
        </p>
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} disabled={loading} className="px-4 py-2 rounded-xl text-button font-medium text-on-surface border border-outline-variant hover:bg-surface-container transition-colors">
            Hủy
          </button>
          <button type="button" onClick={onConfirm} disabled={loading} className="px-4 py-2 rounded-xl text-button font-medium bg-error text-white hover:bg-error/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors flex items-center gap-2">
            {loading && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
            Xóa
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Main component ───────────────────────────────────────────────────────────

type SubTab = 'departments' | 'rooms'

export default function DepartmentRoomManagement() {
  const [subTab, setSubTab] = useState<SubTab>('departments')

  // Departments state
  const [departments, setDepartments] = useState<DepartmentDto[]>([])
  const [deptLoading, setDeptLoading] = useState(false)
  const [deptError, setDeptError] = useState<string | null>(null)
  const [showDeptForm, setShowDeptForm] = useState(false)
  const [editDept, setEditDept] = useState<DepartmentDto | null>(null)
  const [deleteDept, setDeleteDept] = useState<DepartmentDto | null>(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  // Rooms state
  const [rooms, setRooms] = useState<RoomDto[]>([])
  const [roomLoading, setRoomLoading] = useState(false)
  const [roomError, setRoomError] = useState<string | null>(null)
  const [showRoomForm, setShowRoomForm] = useState(false)
  const [editRoom, setEditRoom] = useState<RoomDto | null>(null)
  const [deleteRoom_, setDeleteRoom] = useState<RoomDto | null>(null)
  const [deleteRoomLoading, setDeleteRoomLoading] = useState(false)

  const fetchDepartments = useCallback(async () => {
    setDeptLoading(true)
    setDeptError(null)
    try {
      const res = await listDepartments()
      setDepartments(res.data ?? [])
    } catch {
      setDeptError('Không thể tải danh sách phòng ban.')
    } finally {
      setDeptLoading(false)
    }
  }, [])

  const fetchRooms = useCallback(async () => {
    setRoomLoading(true)
    setRoomError(null)
    try {
      const res = await listRooms()
      setRooms(res.data ?? [])
    } catch {
      setRoomError('Không thể tải danh sách phòng họp.')
    } finally {
      setRoomLoading(false)
    }
  }, [])

  useEffect(() => { fetchDepartments() }, [fetchDepartments])
  useEffect(() => { fetchRooms() }, [fetchRooms])

  const handleDeleteDept = async () => {
    if (!deleteDept) return
    setDeleteLoading(true)
    try {
      await deleteDepartment(deleteDept.id)
      setDeleteDept(null)
      fetchDepartments()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setDeptError(msg ?? 'Không thể xóa phòng ban.')
      setDeleteDept(null)
    } finally {
      setDeleteLoading(false)
    }
  }

  const handleDeleteRoom = async () => {
    if (!deleteRoom_) return
    setDeleteRoomLoading(true)
    try {
      await deleteRoom(deleteRoom_.id)
      setDeleteRoom(null)
      fetchRooms()
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setRoomError(msg ?? 'Không thể xóa phòng họp. Có thể phòng đang có cuộc họp.')
      setDeleteRoom(null)
    } finally {
      setDeleteRoomLoading(false)
    }
  }

  return (
    <div className="space-y-6">
      {/* Sub-tabs */}
      <div className="flex gap-1 border-b border-outline-variant">
        {(['departments', 'rooms'] as SubTab[]).map((tab) => (
          <button
            key={tab}
            onClick={() => setSubTab(tab)}
            className={`inline-flex items-center gap-2 px-4 py-2.5 text-button font-medium border-b-2 transition-colors
              ${subTab === tab
                ? 'border-primary text-primary'
                : 'border-transparent text-on-surface-variant hover:text-on-surface hover:border-outline-variant'
              }`}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">
              {tab === 'departments' ? 'corporate_fare' : 'meeting_room'}
            </span>
            {tab === 'departments' ? 'Phòng ban' : 'Phòng họp'}
          </button>
        ))}
      </div>

      {/* ── Departments ── */}
      {subTab === 'departments' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-body-sm text-on-surface-variant">{departments.length} phòng ban</p>
            <button
              onClick={() => setShowDeptForm(true)}
              className="inline-flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span>
              Thêm phòng ban
            </button>
          </div>

          {deptError && (
            <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert">
              {deptError}
            </div>
          )}

          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
            {deptLoading ? (
              <div className="flex items-center justify-center py-12">
                <div className="w-7 h-7 border-4 border-primary border-t-transparent rounded-full animate-spin" />
              </div>
            ) : departments.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-on-surface-variant">
                <span className="material-symbols-outlined text-4xl mb-2" aria-hidden="true">corporate_fare</span>
                <p className="text-body-sm">Chưa có phòng ban nào</p>
              </div>
            ) : (
              <table className="w-full text-body-sm">
                <thead>
                  <tr className="border-b border-outline-variant bg-surface-container-low">
                    <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">Tên phòng ban</th>
                    <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden md:table-cell">Mô tả</th>
                    <th className="px-4 py-3" aria-label="Hành động" />
                  </tr>
                </thead>
                <tbody>
                  {departments.map((d, idx) => (
                    <tr key={d.id} className={`border-b border-outline-variant last:border-0 hover:bg-surface-container-low transition-colors ${idx % 2 === 0 ? '' : 'bg-surface-container/30'}`}>
                      <td className="px-4 py-3 font-medium text-on-surface">{d.name}</td>
                      <td className="px-4 py-3 text-on-surface-variant hidden md:table-cell">{d.description ?? '—'}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => setEditDept(d)}
                            aria-label={`Chỉnh sửa ${d.name}`}
                            className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-on-surface transition-colors"
                          >
                            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">edit</span>
                          </button>
                          <button
                            onClick={() => setDeleteDept(d)}
                            aria-label={`Xóa ${d.name}`}
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
            )}
          </div>
        </div>
      )}

      {/* ── Rooms ── */}
      {subTab === 'rooms' && (
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <p className="text-body-sm text-on-surface-variant">{rooms.length} phòng họp</p>
            <button
              onClick={() => setShowRoomForm(true)}
              className="inline-flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span>
              Thêm phòng họp
            </button>
          </div>

          {roomError && (
            <div className="bg-error-container text-error rounded-xl px-4 py-3 text-body-sm" role="alert">
              {roomError}
            </div>
          )}

          <div className="bg-surface-container-lowest rounded-xl border border-outline-variant overflow-hidden">
            {roomLoading ? (
              <div className="flex items-center justify-center py-12">
                <div className="w-7 h-7 border-4 border-primary border-t-transparent rounded-full animate-spin" />
              </div>
            ) : rooms.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-on-surface-variant">
                <span className="material-symbols-outlined text-4xl mb-2" aria-hidden="true">meeting_room</span>
                <p className="text-body-sm">Chưa có phòng họp nào</p>
              </div>
            ) : (
              <table className="w-full text-body-sm">
                <thead>
                  <tr className="border-b border-outline-variant bg-surface-container-low">
                    <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold">Tên phòng</th>
                    <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden md:table-cell">Phòng ban</th>
                    <th className="text-left px-4 py-3 text-label-md text-on-surface-variant font-semibold hidden lg:table-cell">Sức chứa</th>
                    <th className="px-4 py-3" aria-label="Hành động" />
                  </tr>
                </thead>
                <tbody>
                  {rooms.map((r, idx) => (
                    <tr key={r.id} className={`border-b border-outline-variant last:border-0 hover:bg-surface-container-low transition-colors ${idx % 2 === 0 ? '' : 'bg-surface-container/30'}`}>
                      <td className="px-4 py-3 font-medium text-on-surface">{r.name}</td>
                      <td className="px-4 py-3 text-on-surface-variant hidden md:table-cell">{r.departmentName ?? '—'}</td>
                      <td className="px-4 py-3 text-on-surface-variant hidden lg:table-cell">
                        {r.capacity ? `${r.capacity} người` : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => setEditRoom(r)}
                            aria-label={`Chỉnh sửa ${r.name}`}
                            className="p-1.5 rounded-lg hover:bg-surface-container text-on-surface-variant hover:text-on-surface transition-colors"
                          >
                            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">edit</span>
                          </button>
                          <button
                            onClick={() => setDeleteRoom(r)}
                            aria-label={`Xóa ${r.name}`}
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
            )}
          </div>
        </div>
      )}

      {/* ── Modals ── */}
      {(showDeptForm || editDept) && (
        <DepartmentFormModal
          dept={editDept ?? undefined}
          onClose={() => { setShowDeptForm(false); setEditDept(null) }}
          onSuccess={() => { setShowDeptForm(false); setEditDept(null); fetchDepartments() }}
        />
      )}

      {deleteDept && (
        <DeleteConfirm
          name={deleteDept.name}
          onClose={() => setDeleteDept(null)}
          onConfirm={handleDeleteDept}
          loading={deleteLoading}
        />
      )}

      {(showRoomForm || editRoom) && (
        <RoomFormModal
          room={editRoom ?? undefined}
          departments={departments}
          onClose={() => { setShowRoomForm(false); setEditRoom(null) }}
          onSuccess={() => { setShowRoomForm(false); setEditRoom(null); fetchRooms() }}
        />
      )}

      {deleteRoom_ && (
        <DeleteConfirm
          name={deleteRoom_.name}
          onClose={() => setDeleteRoom(null)}
          onConfirm={handleDeleteRoom}
          loading={deleteRoomLoading}
        />
      )}
    </div>
  )
}
