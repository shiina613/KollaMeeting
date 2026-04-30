export type UserRole = 'ADMIN' | 'SECRETARY' | 'USER'

export interface User {
  id: number
  username: string
  email: string
  role: UserRole
  departmentId?: number
}
