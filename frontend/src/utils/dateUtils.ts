/**
 * dateUtils — shared datetime formatting helpers.
 * All datetimes are displayed in UTC+7 (Asia/Ho_Chi_Minh).
 * Requirements: 14.8, 14.9
 */

const TZ = 'Asia/Ho_Chi_Minh'

/**
 * Format an ISO8601 datetime string to a human-readable date+time string in UTC+7.
 * Example: "01/05/2025, 10:30"
 */
export function formatDateTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: TZ,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

/**
 * Format an ISO8601 datetime string to a date-only string in UTC+7.
 * Example: "01/05/2025"
 */
export function formatDate(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: TZ,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

/**
 * Format an ISO8601 datetime string to a time-only string in UTC+7.
 * Example: "10:30"
 */
export function formatTime(iso: string): string {
  try {
    return new Intl.DateTimeFormat('vi-VN', {
      timeZone: TZ,
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso))
  } catch {
    return iso
  }
}

/**
 * Convert a datetime-local input value (treated as UTC+7) to an ISO8601 string.
 * datetime-local gives "YYYY-MM-DDTHH:mm" — we append "+07:00".
 */
export function localInputToIso(local: string): string {
  if (!local) return ''
  return new Date(local + ':00+07:00').toISOString()
}

/**
 * Convert an ISO8601 string to a datetime-local input value in UTC+7.
 */
export function isoToLocalInput(iso: string): string {
  if (!iso) return ''
  try {
    const formatter = new Intl.DateTimeFormat('sv-SE', {
      timeZone: TZ,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
    return formatter.format(new Date(iso)).replace(' ', 'T')
  } catch {
    return ''
  }
}

/**
 * Format duration in minutes to a human-readable string.
 * Example: 90 → "1h 30m"
 */
export function formatDuration(minutes?: number | null): string {
  if (minutes === undefined || minutes === null) return '—'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  if (h > 0) return `${h}h ${m}m`
  return `${m} phút`
}
