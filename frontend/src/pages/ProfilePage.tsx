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
      .catch(() => setProfileError('Khong the tai ho so nguoi dung.'))
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
      setProfileSuccess('Da cap nhat ho so.')
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setProfileError(msg ?? 'Khong the cap nhat ho so.')
    } finally {
      setSaving(false)
    }
  }

  const handlePasswordSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setPasswordError(null)
    setPasswordSuccess(null)
    if (passwordForm.newPassword !== confirmPassword) {
      setPasswordError('Mat khau moi va xac nhan mat khau khong khop.')
      return
    }
    setPasswordLoading(true)
    try {
      await changeOwnPassword(passwordForm)
      setPasswordSuccess('Da doi mat khau. Vui long dang nhap lai.')
      setTimeout(() => {
        logout()
        navigate('/login', { replace: true })
      }, 1200)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
      setPasswordError(msg ?? 'Khong the doi mat khau.')
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
        <h1 className="text-h3 font-semibold text-on-surface">Ho so ca nhan</h1>
        <p className="text-body-sm text-on-surface-variant mt-1">
          {user?.fullName ?? user?.username ?? 'Nguoi dung'}
        </p>
      </div>

      <form
        onSubmit={handleProfileSubmit}
        className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 space-y-5"
      >
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <ProfileInput label="Ma nhan vien" value={employeeCode} disabled />
          <ProfileInput label="Phong ban" value={departmentName || 'Khong co phong ban'} disabled />
          <ProfileInput
            label="Ho va ten"
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
            label="Ngay sinh"
            value={form.dob ?? ''}
            type="date"
            onChange={(value) => updateField('dob', value)}
          />
          <ProfileInput
            label="So dien thoai"
            value={form.phoneNumber ?? ''}
            onChange={(value) => updateField('phoneNumber', value)}
          />
          <ProfileInput
            label="Hoc vi"
            value={form.degree ?? ''}
            onChange={(value) => updateField('degree', value)}
          />
          <ProfileInput
            label="CCCD/CMND"
            value={form.identification ?? ''}
            onChange={(value) => updateField('identification', value)}
          />
          <ProfileInput
            label="Ngan hang"
            value={form.bankName ?? ''}
            onChange={(value) => updateField('bankName', value)}
          />
          <ProfileInput
            label="So tai khoan"
            value={form.bankNumber ?? ''}
            onChange={(value) => updateField('bankNumber', value)}
          />
          <ProfileInput
            label="Anh dai dien"
            value={form.img ?? ''}
            onChange={(value) => updateField('img', value)}
          />
          <div className="sm:col-span-2">
            <label className="block text-label-md text-on-surface-variant mb-1">
              Dia chi
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
            Luu ho so
          </button>
        </div>
      </form>

      <form
        onSubmit={handlePasswordSubmit}
        className="bg-surface-container-lowest rounded-xl border border-outline-variant p-6 space-y-4"
      >
        <h2 className="text-body-md font-semibold text-on-surface">Doi mat khau</h2>
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <ProfileInput
            label="Mat khau hien tai"
            value={passwordForm.currentPassword}
            type="password"
            required
            onChange={(value) => setPasswordForm((prev) => ({ ...prev, currentPassword: value }))}
          />
          <ProfileInput
            label="Mat khau moi"
            value={passwordForm.newPassword}
            type="password"
            required
            onChange={(value) => setPasswordForm((prev) => ({ ...prev, newPassword: value }))}
          />
          <ProfileInput
            label="Xac nhan mat khau"
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
            Doi mat khau
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
