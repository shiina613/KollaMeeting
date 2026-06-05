import { describe, it, expect } from 'vitest'
import {
  getQualityLevel,
  getBarCount,
  formatQualityTooltip,
  type ConnectionStats,
} from '../connectionQuality'

describe('getQualityLevel', () => {
  it('returns "unavailable" when stats is null', () => {
    expect(getQualityLevel(null)).toBe('unavailable')
  })

  it('returns "good" when latency < 100 and packetLoss is 0', () => {
    expect(getQualityLevel({ latency: 50, packetLoss: 0 })).toBe('good')
    expect(getQualityLevel({ latency: 0, packetLoss: 0 })).toBe('good')
    expect(getQualityLevel({ latency: 99, packetLoss: 0 })).toBe('good')
  })

  it('returns "moderate" when latency is 100-300 and packetLoss <= 5', () => {
    expect(getQualityLevel({ latency: 100, packetLoss: 0 })).toBe('moderate')
    expect(getQualityLevel({ latency: 200, packetLoss: 3 })).toBe('moderate')
    expect(getQualityLevel({ latency: 300, packetLoss: 5 })).toBe('moderate')
  })

  it('returns "moderate" when latency < 100 but packetLoss > 0 and <= 5', () => {
    expect(getQualityLevel({ latency: 50, packetLoss: 1 })).toBe('moderate')
    expect(getQualityLevel({ latency: 50, packetLoss: 5 })).toBe('moderate')
  })

  it('returns "poor" when latency > 300', () => {
    expect(getQualityLevel({ latency: 301, packetLoss: 0 })).toBe('poor')
    expect(getQualityLevel({ latency: 1000, packetLoss: 0 })).toBe('poor')
  })

  it('returns "poor" when packetLoss > 5', () => {
    expect(getQualityLevel({ latency: 50, packetLoss: 6 })).toBe('poor')
    expect(getQualityLevel({ latency: 50, packetLoss: 100 })).toBe('poor')
  })

  it('returns "poor" when both latency > 300 and packetLoss > 5', () => {
    expect(getQualityLevel({ latency: 500, packetLoss: 10 })).toBe('poor')
  })
})

describe('getBarCount', () => {
  it('returns 4 for "good"', () => {
    expect(getBarCount('good')).toBe(4)
  })

  it('returns 2 for "moderate"', () => {
    expect(getBarCount('moderate')).toBe(2)
  })

  it('returns 1 for "poor"', () => {
    expect(getBarCount('poor')).toBe(1)
  })

  it('returns 0 for "unavailable"', () => {
    expect(getBarCount('unavailable')).toBe(0)
  })
})

describe('formatQualityTooltip', () => {
  it('returns "No data" when stats is null', () => {
    expect(formatQualityTooltip(null)).toBe('No data')
  })

  it('includes latency value in ms', () => {
    const stats: ConnectionStats = { latency: 150, packetLoss: 2 }
    const tooltip = formatQualityTooltip(stats)
    expect(tooltip).toContain('150ms')
  })

  it('includes packet loss value as percentage', () => {
    const stats: ConnectionStats = { latency: 150, packetLoss: 2 }
    const tooltip = formatQualityTooltip(stats)
    expect(tooltip).toContain('2%')
  })

  it('contains both latency and packet loss for any valid stats', () => {
    const stats: ConnectionStats = { latency: 0, packetLoss: 0 }
    const tooltip = formatQualityTooltip(stats)
    expect(tooltip).toContain('0ms')
    expect(tooltip).toContain('0%')
  })
})
