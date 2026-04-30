import { create } from 'zustand'
import type { User } from '../types/user'

interface AuthState {
  // State
  token: string | null
  user: User | null
  isAuthenticated: boolean

  // Actions
  login: (token: string, user: User) => void
  logout: () => void
  setUser: (user: User) => void
}

/**
 * Auth store — JWT token is kept in Zustand memory ONLY.
 * NEVER persisted to localStorage or sessionStorage.
 * Requirements: 2.2, 19.6
 */
const useAuthStore = create<AuthState>((set) => ({
  // Initial state — unauthenticated
  token: null,
  user: null,
  isAuthenticated: false,

  // Set token + user, mark as authenticated
  login: (token: string, user: User) => {
    set({ token, user, isAuthenticated: true })
  },

  // Clear all auth state
  logout: () => {
    set({ token: null, user: null, isAuthenticated: false })
  },

  // Update user info without changing token
  setUser: (user: User) => {
    set({ user })
  },
}))

export default useAuthStore
