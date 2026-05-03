/**
 * LoginPage — public route for user authentication.
 * Requirements: 1.4, 1.5, 2.1–2.4
 */

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import useAuthStore from '../store/authStore'
import type { ApiResponse } from '../types/api'
import type { User } from '../types/user'

interface LoginResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
  user: {
    id: number
    username: string
    fullName: string
    role: User['role']
    email: string
  }
}

export default function LoginPage() {
  const navigate = useNavigate()
  const { login, isAuthenticated } = useAuthStore()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Already authenticated → redirect to dashboard
  if (isAuthenticated) {
    navigate('/', { replace: true })
    return null
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password) return

    setLoading(true)
    setError(null)

    try {
      const res = await api.post<ApiResponse<LoginResponse>>('/auth/login', {
        username: username.trim(),
        password,
      })
      const { accessToken, user: userInfo } = res.data.data
      const user: User = {
        id: userInfo.id,
        username: userInfo.username,
        email: userInfo.email,
        role: userInfo.role,
      }
      login(accessToken, user)
      navigate('/', { replace: true })
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 401 || status === 400) {
        setError('Tên đăng nhập hoặc mật khẩu không đúng.')
      } else {
        setError('Đã xảy ra lỗi. Vui lòng thử lại.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="text-center mb-8">
          <h1 className="text-3xl font-black text-primary uppercase tracking-wider">Kolla</h1>
          <p className="text-body-sm text-on-surface-variant mt-1 uppercase tracking-widest font-semibold">
            Meeting
          </p>
        </div>

        {/* Card */}
        <div className="bg-surface-container-lowest rounded-xl border border-outline-variant p-8 shadow-sm">
          <h2 className="text-h3 font-semibold text-on-surface mb-6">Đăng nhập</h2>

          {error && (
            <div
              className="bg-error-container text-error rounded-lg px-3 py-2 text-body-sm mb-4"
              role="alert"
              data-testid="login-error"
            >
              {error}
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate className="space-y-4">
            <div>
              <label
                htmlFor="username"
                className="block text-label-md text-on-surface-variant mb-1"
              >
                Tên đăng nhập
              </label>
              <input
                id="username"
                type="text"
                autoComplete="username"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                data-testid="login-username"
                className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                           text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-label-md text-on-surface-variant mb-1"
              >
                Mật khẩu
              </label>
              <input
                id="password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                data-testid="login-password"
                className="w-full border border-outline-variant rounded-lg px-3 py-2 text-body-sm
                           text-on-surface bg-surface focus:outline-none focus:ring-2 focus:ring-primary"
              />
            </div>

            <button
              type="submit"
              disabled={loading || !username.trim() || !password}
              data-testid="login-submit"
              className="w-full bg-primary text-white py-2.5 rounded-xl text-button font-medium
                         hover:bg-primary/90 disabled:opacity-60 disabled:cursor-not-allowed
                         transition-colors flex items-center justify-center gap-2 mt-2"
            >
              {loading && (
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              )}
              {loading ? 'Đang đăng nhập...' : 'Đăng nhập'}
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
