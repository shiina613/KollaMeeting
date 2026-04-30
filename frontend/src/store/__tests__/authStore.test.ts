import { describe, it, expect, beforeEach } from 'vitest'
import useAuthStore from '../authStore'
import type { User } from '../../types/user'

// ─── Test fixtures ────────────────────────────────────────────────────────────

const mockUser: User = {
  id: 1,
  username: 'testuser',
  email: 'test@example.com',
  role: 'USER',
  departmentId: 10,
}

const mockAdminUser: User = {
  id: 2,
  username: 'admin',
  email: 'admin@example.com',
  role: 'ADMIN',
}

const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature'

// ─── Reset store before each test ────────────────────────────────────────────

beforeEach(() => {
  useAuthStore.getState().logout()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('authStore — initial state', () => {
  it('should start unauthenticated', () => {
    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(false)
    expect(state.token).toBeNull()
    expect(state.user).toBeNull()
  })
})

describe('authStore — login action', () => {
  it('should set token and user on login', () => {
    useAuthStore.getState().login(mockToken, mockUser)

    const state = useAuthStore.getState()
    expect(state.token).toBe(mockToken)
    expect(state.user).toEqual(mockUser)
    expect(state.isAuthenticated).toBe(true)
  })

  it('should set isAuthenticated to true after login', () => {
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
    useAuthStore.getState().login(mockToken, mockUser)
    expect(useAuthStore.getState().isAuthenticated).toBe(true)
  })

  it('should store user role correctly', () => {
    useAuthStore.getState().login(mockToken, mockAdminUser)
    expect(useAuthStore.getState().user?.role).toBe('ADMIN')
  })

  it('should store user departmentId when provided', () => {
    useAuthStore.getState().login(mockToken, mockUser)
    expect(useAuthStore.getState().user?.departmentId).toBe(10)
  })

  it('should allow departmentId to be undefined', () => {
    useAuthStore.getState().login(mockToken, mockAdminUser)
    expect(useAuthStore.getState().user?.departmentId).toBeUndefined()
  })
})

describe('authStore — logout action', () => {
  it('should clear token and user on logout', () => {
    useAuthStore.getState().login(mockToken, mockUser)
    useAuthStore.getState().logout()

    const state = useAuthStore.getState()
    expect(state.token).toBeNull()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })

  it('should set isAuthenticated to false after logout', () => {
    useAuthStore.getState().login(mockToken, mockUser)
    expect(useAuthStore.getState().isAuthenticated).toBe(true)

    useAuthStore.getState().logout()
    expect(useAuthStore.getState().isAuthenticated).toBe(false)
  })

  it('should be safe to call logout when already logged out', () => {
    expect(() => useAuthStore.getState().logout()).not.toThrow()
    const state = useAuthStore.getState()
    expect(state.token).toBeNull()
    expect(state.user).toBeNull()
    expect(state.isAuthenticated).toBe(false)
  })
})

describe('authStore — setUser action', () => {
  it('should update user without changing token', () => {
    useAuthStore.getState().login(mockToken, mockUser)

    const updatedUser: User = { ...mockUser, email: 'updated@example.com' }
    useAuthStore.getState().setUser(updatedUser)

    const state = useAuthStore.getState()
    expect(state.user?.email).toBe('updated@example.com')
    expect(state.token).toBe(mockToken)
    expect(state.isAuthenticated).toBe(true)
  })
})

describe('authStore — localStorage isolation', () => {
  it('should NOT persist token to localStorage', () => {
    useAuthStore.getState().login(mockToken, mockUser)

    // Check that token is NOT in localStorage
    const localStorageKeys = Object.keys(localStorage)
    const hasTokenInStorage = localStorageKeys.some((key) => {
      const value = localStorage.getItem(key)
      return value !== null && value.includes(mockToken)
    })

    expect(hasTokenInStorage).toBe(false)
  })

  it('should NOT persist user to localStorage', () => {
    useAuthStore.getState().login(mockToken, mockUser)

    // Check that user data is NOT in localStorage
    const localStorageKeys = Object.keys(localStorage)
    const hasUserInStorage = localStorageKeys.some((key) => {
      const value = localStorage.getItem(key)
      return value !== null && value.includes(mockUser.username)
    })

    expect(hasUserInStorage).toBe(false)
  })

  it('should NOT persist auth state to sessionStorage', () => {
    useAuthStore.getState().login(mockToken, mockUser)

    const sessionStorageKeys = Object.keys(sessionStorage)
    const hasTokenInSession = sessionStorageKeys.some((key) => {
      const value = sessionStorage.getItem(key)
      return value !== null && value.includes(mockToken)
    })

    expect(hasTokenInSession).toBe(false)
  })
})
