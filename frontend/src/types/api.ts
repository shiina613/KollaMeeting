/**
 * Shared API response wrapper types.
 * Requirements: 1.4
 */

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number // current page (0-indexed)
}

export interface ApiResponse<T> {
  data: T
  message?: string
  success: boolean
}
