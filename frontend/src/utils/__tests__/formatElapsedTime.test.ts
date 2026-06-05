/**
 * Unit tests for formatElapsedTime utility.
 * Requirements: 4.1, 4.3, 4.4
 */

import { describe, it, expect } from 'vitest'
import { formatElapsedTime } from '../formatElapsedTime'

describe('formatElapsedTime', () => {
  it('returns "00:00" for 0 seconds', () => {
    expect(formatElapsedTime(0)).toBe('00:00')
  })

  it('returns "00:01" for 1 second', () => {
    expect(formatElapsedTime(1)).toBe('00:01')
  })

  it('returns "01:00" for 60 seconds', () => {
    expect(formatElapsedTime(60)).toBe('01:00')
  })

  it('returns "59:59" for 3599 seconds (just under 1 hour)', () => {
    expect(formatElapsedTime(3599)).toBe('59:59')
  })

  it('returns "01:00:00" for exactly 3600 seconds (1 hour)', () => {
    expect(formatElapsedTime(3600)).toBe('01:00:00')
  })

  it('returns "01:30:45" for 5445 seconds', () => {
    expect(formatElapsedTime(5445)).toBe('01:30:45')
  })

  it('returns "24:00:00" for 86400 seconds (24 hours)', () => {
    expect(formatElapsedTime(86400)).toBe('24:00:00')
  })

  it('zero-pads single-digit minutes and seconds in MM:SS format', () => {
    expect(formatElapsedTime(65)).toBe('01:05')
  })

  it('zero-pads hours, minutes, and seconds in HH:MM:SS format', () => {
    expect(formatElapsedTime(3661)).toBe('01:01:01')
  })
})
