import { describe, it, expect } from 'vitest'
import { formatJavaZonedTime, parseJavaZonedDateTime } from '../parseJavaZonedDateTime'

describe('parseJavaZonedDateTime', () => {
  it('parses Java ZonedDateTime with bracketed zone id', () => {
    const date = parseJavaZonedDateTime('2026-05-22T10:30:00+07:00[Asia/Ho_Chi_Minh]')
    expect(date).not.toBeNull()
    expect(date!.getHours()).toBeTypeOf('number')
  })

  it('formats time without Invalid Date', () => {
    const label = formatJavaZonedTime('2026-05-22T10:30:00+07:00[Asia/Ho_Chi_Minh]')
    expect(label).not.toBe('—')
    expect(label).not.toContain('Invalid')
  })
})
