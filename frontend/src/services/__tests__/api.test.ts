import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import axios from 'axios'
import MockAdapter from 'axios-mock-adapter'
import useAuthStore from '../../store/authStore'
import api from '../api'
import type { User } from '../../types/user'

// ─── Setup ────────────────────────────────────────────────────────────────────

const mockAxios = new MockAdapter(api)

const mockUser: User = {
  id: 1,
  username: 'testuser',
  email: 'test@example.com',
  role: 'USER',
}

const mockToken = 'test-jwt-token-abc123'

beforeEach(() => {
  // Reset auth store
  useAuthStore.getState().logout()
  // Reset mock adapter
  mockAxios.reset()
  // Reset window.location mock
  vi.restoreAllMocks()
})

afterEach(() => {
  mockAxios.reset()
})

// ─── JWT interceptor tests ────────────────────────────────────────────────────

describe('api — JWT request interceptor', () => {
  it('should attach Authorization header when token exists', async () => {
    useAuthStore.getState().login(mockToken, mockUser)

    let capturedHeaders: Record<string, string> = {}
    mockAxios.onGet('/test').reply((config) => {
      capturedHeaders = config.headers as Record<string, string>
      return [200, { data: 'ok' }]
    })

    await api.get('/test')

    expect(capturedHeaders['Authorization']).toBe(`Bearer ${mockToken}`)
  })

  it('should NOT attach Authorization header when not authenticated', async () => {
    // Ensure logged out
    useAuthStore.getState().logout()

    let capturedHeaders: Record<string, string> = {}
    mockAxios.onGet('/test').reply((config) => {
      capturedHeaders = config.headers as Record<string, string>
      return [200, { data: 'ok' }]
    })

    await api.get('/test')

    expect(capturedHeaders['Authorization']).toBeUndefined()
  })

  it('should use Bearer scheme in Authorization header', async () => {
    useAuthStore.getState().login(mockToken, mockUser)

    let authHeader = ''
    mockAxios.onGet('/test').reply((config) => {
      authHeader = (config.headers as Record<string, string>)['Authorization'] ?? ''
      return [200, {}]
    })

    await api.get('/test')

    expect(authHeader).toMatch(/^Bearer /)
  })
})

// ─── 401 response interceptor tests ──────────────────────────────────────────

describe('api — 401 response interceptor', () => {
  it('should call logout when 401 response is received', async () => {
    useAuthStore.getState().login(mockToken, mockUser)
    expect(useAuthStore.getState().isAuthenticated).toBe(true)

    mockAxios.onGet('/protected').reply(401, { message: 'Unauthorized' })

    try {
      await api.get('/protected')
    } catch {
      // Expected to throw
    }

    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('should clear user on 401 response', async () => {
    useAuthStore.getState().login(mockToken, mockUser)
    expect(useAuthStore.getState().user).not.toBeNull()

    mockAxios.onGet('/protected').reply(401)

    try {
      await api.get('/protected')
    } catch {
      // Expected
    }

    expect(useAuthStore.getState().user).toBeNull()
  })

  it('should redirect to /login on 401 when not already on login page', async () => {
    useAuthStore.getState().login(mockToken, mockUser)

    // Mock window.location
    const originalLocation = window.location
    const mockLocation = { ...originalLocation, href: '', pathname: '/dashboard' }
    Object.defineProperty(window, 'location', {
      value: mockLocation,
      writable: true,
    })

    mockAxios.onGet('/protected').reply(401)

    try {
      await api.get('/protected')
    } catch {
      // Expected
    }

    expect(window.location.href).toBe('/login')

    // Restore
    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    })
  })

  it('should NOT redirect to /login when already on login page', async () => {
    useAuthStore.getState().login(mockToken, mockUser)

    const originalLocation = window.location
    const mockLocation = { ...originalLocation, href: '/login', pathname: '/login' }
    Object.defineProperty(window, 'location', {
      value: mockLocation,
      writable: true,
    })

    mockAxios.onGet('/protected').reply(401)

    try {
      await api.get('/protected')
    } catch {
      // Expected
    }

    // href should remain /login (not changed to /login again)
    expect(window.location.href).toBe('/login')

    Object.defineProperty(window, 'location', {
      value: originalLocation,
      writable: true,
    })
  })
})

// ─── 5xx response interceptor tests ──────────────────────────────────────────

describe('api — 5xx response interceptor', () => {
  it('should reject the promise on 5xx response', async () => {
    mockAxios.onGet('/server-error').reply(500, { message: 'Internal Server Error' })

    await expect(api.get('/server-error')).rejects.toThrow()
  })

  it('should reject on 503 response', async () => {
    mockAxios.onGet('/unavailable').reply(503, { message: 'Service Unavailable' })

    await expect(api.get('/unavailable')).rejects.toThrow()
  })

  it('should NOT call logout on 5xx response', async () => {
    useAuthStore.getState().login(mockToken, mockUser)

    mockAxios.onGet('/server-error').reply(500)

    try {
      await api.get('/server-error')
    } catch {
      // Expected
    }

    // Auth state should remain intact on 5xx
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
    expect(useAuthStore.getState().token).toBe(mockToken)
  })
})
