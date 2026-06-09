import { useEffect, useState, type FormEvent, type HTMLInputTypeAttribute } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  changeOwnPassword,
  getCurrentUser,
  updateCurrentUser,
  type ChangePasswordRequest,
  type UpdateUserRequest,
} from '../services/userService'
import useAuthStore from '../store/authStore'

type ProfileForm = Pick<
  UpdateUserRequest,
  | 'fullName'
  | 'email'
  | 'dob'
  | 'phoneNumber'
  | 'degree'
  | 'identification'
  | 'address'
  | 'bankName'
  | 'bankNumber'
  | 'img'
>

const EMPTY_PROFILE: ProfileForm = {
  fullName: '',
  email: '',
  dob: '',
  phoneNumber: '',
  degree: '',
  identification: '',
  address: '',
  bankName: '',
  bankNumber: '',
  img: '',
}

export default function ProfilePage() {
  const navigate = useNavigate()
  const { user, setUser, logout } = useAuthStore()
  const [form, setForm] = useState<ProfileForm>(EMPTY_PROFILE)
  const [employeeCode, setEmployeeCode] = useState('')
  const [departmentName, setDepartmentName] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [profileError, setProfileError] = useState<string | null>(null)
  const [profileSuccess, setProfileSuccess] = useState<string | null>(null)
  const [passwordForm, setPasswordForm] = useState<ChangePasswordRequest>({
    currentPassword: '',
    newPassword: '',
  })
  const [confirmPassword, setConfirmPassword] = useState('')
  const [passwordLoading, setPasswordLoading] = useState(false)
  const [passwordError, setPasswordError] = useState<string | null>(null)
  const [passwordSuccess, setPasswordSuccess] = useState<string | null>(null)

  useEffect(() => {
    let mounted = true
    getCurrentUser()
      .then((res) => {
        if (!mounted) return
        const current = res.data
        setUser(current)
        setEmployeeCode(current.employeeCode ?? current.username ?? '')
        setDepartmentName(current.departmentName ?? current.department?.name ?? '')
        setForm({
          fullName: current.fullName ?? '',
          email: current.email ?? '',
          dob: current.dob ?? '',
          phoneNumber: current.phoneNumber ?? '',
          degree: current.degree ?? '',
          identification: current.identification ?? '',
          address: current.address ?? '',
          bankName: current.bankName ?? '',
          bankNumber: current.bankNumber ?? '',
          img: current.img ?? '',
        })
      })
      .catch(() => setProfileError('Không thể tải hồ sơ người dùng.'))
      .finally(() => {
        if (mounted) setLoading(false)
      })
    return () => {
      mounted = false
    }
  }, [setUser])

  const updateField = <K extends keyof ProfileForm>(field: K, value: ProfileForm[K]) => {
    setForm((prev) => ({ ...prev, [field]: value }))
    setProfileError(null)
    setProfileSuccess(null)
  }

  const handleProfileSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setSaving(true)
    setProfileError(null)
    setProfileSuccess(null)
    try {
      const res = await updateCurrentUser({
        ...form,
        fullName: form.fullName?.trim(),
        email: form.email?.trim(),
      })
      setUser(res.data)
      setProfileSuccess('Đã cập nhật hồ sơ.')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setProfileError(msg ?? 'Không thể cập nhật hồ sơ.')
    } finally {
      setSaving(false)
    }
  }

  const handlePasswordSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setPasswordError(null)
    setPasswordSuccess(null)
    if (passwordForm.newPassword !== confirmPassword) {
      setPasswordError('Mật khẩu mới và xác nhận mật khẩu không khớp.')
      return
    }
    setPasswordLoading(true)
    try {
      await changeOwnPassword(passwordForm)
      setPasswordSuccess('Đã đổi mật khẩu. Vui lòng đăng nhập lại.')
      setTimeout(() => {
        logout()
        navigate('/login', { replace: true })
      }, 1200)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setPasswordError(msg ?? 'Không thể đổi mật khẩu.')
    } finally {
      setPasswordLoading(false)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
      </div>
    )
  }

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-h3 font-semibold text-on-surface">Hồ sơ cá nhân</h1>
        <p className="text-body-sm text-on-surface-variant mt-1">
          {user?.fullName ?? user?.username ?? 'Người dùng'}
        </p>
      </div>

      <form
        onSubmit={handleProfileSubmit}
        className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 space-y-5"
      >
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <ProfileInput label="Mã nhân viên" value={employeeCode} disabled />
          <ProfileInput label="Phòng ban" value={departmentName || 'Không có phòng ban'} disabled />
          <ProfileInput
            label="Họ và tên"
            value={form.fullName ?? ''}
            required
            onChange={(value) => updateField('fullName', value)}
          />
          <ProfileInput
            label="Email"
            value={form.email ?? ''}
            type="email"
            required
            onChange={(value) => updateField('email', value)}
          />
          <ProfileInput
            label="Ngày sinh"
            value={form.dob ?? ''}
            type="date"
            onChange={(value) => updateField('dob', value)}
          />
          <ProfileInput
            label="Số điện thoại"
            value={form.phoneNumber ?? ''}
            onChange={(value) => updateField('phoneNumber', value)}
          />
          <ProfileInput
            label="Học vị"
            value={form.degree ?? ''}
            onChange={(value) => updateField('degree', value)}
          />
          <ProfileInput
            label="CCCD/CMND"
            value={form.identification ?? ''}
            onChange={(value) => updateField('identification', value)}
          />
          <ProfileInput
            label="Ngân hàng"
            value={form.bankName ?? ''}
            onChange={(value) => updateField('bankName', value)}
          />
          <ProfileInput
            label="Số tài khoản"
            value={form.bankNumber ?? ''}
            onChange={(value) => updateField('bankNumber', value)}
          />
          <ProfileInput
            label="Ảnh đại diện"
            value={form.img ?? ''}
            onChange={(value) => updateField('img', value)}
          />
          <div className="sm:col-span-2">
            <label className="block text-label-md text-on-surface-variant mb-1">
              Địa chỉ
            </label>
            <textarea
              value={form.address ?? ''}
              onChange={(event) => updateField('address', event.target.value)}
              rows={3}
              className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary resize-none"
            />
          </div>
        </div>

        {profileError && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm" role="alert">
            {profileError}
          </div>
        )}
        {profileSuccess && (
          <div className="bg-green-100 text-green-700 rounded-lg px-3 py-2 text-body-sm">
            {profileSuccess}
          </div>
        )}

        <div className="flex justify-end border-t border-outline-variant pt-4">
          <button
            type="submit"
            disabled={saving}
            className="inline-flex items-center gap-2 bg-primary text-white px-4 py-2 rounded-xl text-button font-medium hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            {saving && <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />}
            Lưu hồ sơ
          </button>
        </div>
      </form>

      <form
        onSubmit={handlePasswordSubmit}
        className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 space-y-4"
      >
        <h2 className="text-body-md font-semibold text-on-surface">Đổi mật khẩu</h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <ProfileInput
            label="Mật khẩu hiện tại"
            value={passwordForm.currentPassword}
            type="password"
            required
            onChange={(value) => setPasswordForm((prev) => ({ ...prev, currentPassword: value }))}
          />
          <ProfileInput
            label="Mật khẩu mới"
            value={passwordForm.newPassword}
            type="password"
            required
            onChange={(value) => setPasswordForm((prev) => ({ ...prev, newPassword: value }))}
          />
          <ProfileInput
            label="Xác nhận mật khẩu"
            value={confirmPassword}
            type="password"
            required
            onChange={setConfirmPassword}
          />
        </div>
        {passwordError && (
          <div className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm" role="alert">
            {passwordError}
          </div>
        )}
        {passwordSuccess && (
          <div className="bg-green-100 text-green-700 rounded-lg px-3 py-2 text-body-sm">
            {passwordSuccess}
          </div>
        )}
        <div className="flex justify-end border-t border-outline-variant pt-4">
          <button
            type="submit"
            disabled={passwordLoading}
            className="inline-flex items-center gap-2 border border-outline-variant text-on-surface px-4 py-2 rounded-xl text-button font-medium hover:bg-surface-container disabled:opacity-60 disabled:cursor-not-allowed transition-colors"
          >
            {passwordLoading && <div className="w-4 h-4 border-2 border-primary border-t-transparent rounded-full animate-spin" />}
            Đổi mật khẩu
          </button>
        </div>
      </form>
    </div>
  )
}

function ProfileInput({
  label,
  value,
  onChange,
  type = 'text',
  disabled = false,
  required = false,
}: {
  label: string
  value: string
  onChange?: (value: string) => void
  type?: HTMLInputTypeAttribute
  disabled?: boolean
  required?: boolean
}) {
  return (
    <div>
      <label className="block text-label-md text-on-surface-variant mb-1">
        {label}
        {required && <span className="text-error"> *</span>}
      </label>
      <input
        type={type}
        value={value}
        required={required}
        disabled={disabled}
        onChange={(event) => onChange?.(event.target.value)}
        className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary disabled:bg-surface-container disabled:text-on-surface-variant"
      />
    </div>
  )
}
