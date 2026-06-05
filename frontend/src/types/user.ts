export type UserRole = 'ADMIN' | 'SECRETARY' | 'USER'

export interface UserProfileFields {
  employeeCode?: string
  dob?: string
  phoneNumber?: string
  degree?: string
  identification?: string
  address?: string
  bankName?: string
  bankNumber?: string
  img?: string
}

export interface User {
  id: number
  username: string
  fullName?: string
  email: string
  role: UserRole
  departmentId?: number
  departmentName?: string
  isActive?: boolean
  department?: {
    id: number
    name: string
    departmentCode?: string
  }
  employeeCode?: string
  dob?: string
  phoneNumber?: string
  degree?: string
  identification?: string
  address?: string
  bankName?: string
  bankNumber?: string
  img?: string
}
