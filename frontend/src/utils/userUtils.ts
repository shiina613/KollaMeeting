/**
 * Utility functions for displaying user information.
 */

interface UserDisplayInfo {
  id: number
  fullName: string
  departmentName?: string | null
}

/**
 * Format a user for display as "Tên - Phòng ban - #ID".
 * If departmentName is not available, falls back to "Tên - #ID".
 *
 * Examples:
 *   formatUserLabel({ id: 3, fullName: 'Nguyễn Văn A', departmentName: 'Kỹ thuật' })
 *   → "Nguyễn Văn A - Kỹ thuật - #3"
 *
 *   formatUserLabel({ id: 3, fullName: 'Nguyễn Văn A' })
 *   → "Nguyễn Văn A - #3"
 */
export function formatUserLabel(user: UserDisplayInfo): string {
  if (user.departmentName) {
    return `${user.fullName} - ${user.departmentName} - #${user.id}`
  }
  return `${user.fullName} - #${user.id}`
}

/**
 * Format a meeting's host or secretary from flat backend fields.
 * Falls back to '—' if no name is available.
 *
 * @param id   hostId or secretaryId
 * @param name hostName or secretaryName
 * @param dept hostDepartmentName or secretaryDepartmentName
 */
export function formatMeetingUserLabel(
  id: number | undefined | null,
  name: string | undefined | null,
  dept: string | undefined | null,
): string {
  if (!name) return '—'
  if (dept && id != null) return `${name} - ${dept} - #${id}`
  if (id != null) return `${name} - #${id}`
  return name
}
