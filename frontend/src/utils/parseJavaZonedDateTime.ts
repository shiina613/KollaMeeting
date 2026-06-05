/**
 * Parse ISO-like timestamps from the Java backend.
 *
 * Spring often serializes ZonedDateTime as e.g.
 * "2026-05-22T10:30:00+07:00[Asia/Ho_Chi_Minh]" — trailing [Zone/ID] breaks Date.parse.
 */

export function normalizeJavaZonedDateTime(iso: string): string {
  const trimmed = iso.trim().replace(/\[.*\]$/, '').trim()
  if (!trimmed) return ''
  if (/[Z]$|[+-]\d{2}:\d{2}$/.test(trimmed)) return trimmed
  return `${trimmed}+07:00`
}

export function parseJavaZonedDateTime(iso: string): Date | null {
  const normalized = normalizeJavaZonedDateTime(iso)
  if (!normalized) return null
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

export function formatJavaZonedTime(
  iso: string,
  options?: Intl.DateTimeFormatOptions,
): string {
  const date = parseJavaZonedDateTime(iso)
  if (!date) return '—'
  return date.toLocaleTimeString('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    timeZone: 'Asia/Ho_Chi_Minh',
    ...options,
  })
}
