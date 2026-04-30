import axios, { type AxiosInstance, type InternalAxiosRequestConfig, type AxiosResponse, type AxiosError } from 'axios'
import useAuthStore from '../store/authStore'
import { toast } from '../utils/toast'

/**
 * Base URL from environment variable.
 * Default: http://localhost:8080/api/v1
 * Requirements: 2.3, 2.4, 15.3
 */
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api/v1'

const api: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 30_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * Request interceptor — attach JWT Bearer token from Zustand authStore.
 * Token is read from in-memory store (never localStorage).
 * Requirements: 2.3
 */
api.interceptors.request.use(
  (config: InternalAxiosRequestConfig): InternalAxiosRequestConfig => {
    const token = useAuthStore.getState().token
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error),
)

/**
 * Response interceptor:
 * - 401 → clear auth state and redirect to /login
 * - 5xx → show toast error
 * Requirements: 2.4, 15.3
 */
api.interceptors.response.use(
  (response: AxiosResponse): AxiosResponse => response,
  (error: AxiosError) => {
    const status = error.response?.status

    if (status === 401) {
      // Token expired or invalid — clear auth and redirect to login
      useAuthStore.getState().logout()
      // Use window.location to avoid circular dependency with React Router
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    } else if (status !== undefined && status >= 500) {
      // Server error — show toast
      const message =
        (error.response?.data as { message?: string })?.message ??
        'Đã xảy ra lỗi máy chủ. Vui lòng thử lại sau.'
      toast.error(message)
    }

    return Promise.reject(error)
  },
)

export default api
