import { create } from 'zustand'
import { persist, createJSONStorage } from 'zustand/middleware'
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
 * Auth store — JWT token is persisted to sessionStorage so it survives
 * page reloads within the same browser tab, but is cleared when the tab
 * is closed (unlike localStorage).
 * Requirements: 2.2, 19.6
 */
const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
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
    }),
    {
      name: 'kolla-auth',
      storage: createJSONStorage(() => sessionStorage),
      // Only persist the fields needed to restore session
      partialize: (state) => ({
        token: state.token,
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    },
  ),
)

export default useAuthStore
