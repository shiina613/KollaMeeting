/**
 * Unit tests for SkeletonLoader component.
 *
 * Tests:
 * - Renders correct number of rows for participant variant
 * - Renders correct number of rows for raise-hand variant
 * - Participant variant includes avatar circle and two text lines
 * - Raise-hand variant includes icon circle and single text line
 * - Shimmer animation classes are applied (animate-pulse + gradient)
 * - Accessible role and aria-label are present
 *
 * Requirements: 14.1, 14.2, 14.3, 14.4
 */

import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import SkeletonLoader from '../SkeletonLoader'

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('SkeletonLoader', () => {
  describe('participant variant', () => {
    it('renders the correct number of rows', () => {
      render(<SkeletonLoader rows={4} variant="participant" />)

      const rows = screen.getAllByTestId('skeleton-row-participant')
      expect(rows).toHaveLength(4)
    })

    it('renders avatar circle and two text lines per row', () => {
      render(<SkeletonLoader rows={1} variant="participant" />)

      const row = screen.getByTestId('skeleton-row-participant')
      const shimmerBlocks = row.querySelectorAll('[aria-hidden="true"]')

      // 1 avatar circle + 2 text lines = 3 shimmer blocks
      expect(shimmerBlocks).toHaveLength(3)

      // Avatar circle should be round
      expect(shimmerBlocks[0]).toHaveClass('rounded-full')
      // Text lines should be regular rounded
      expect(shimmerBlocks[1]).toHaveClass('rounded')
      expect(shimmerBlocks[2]).toHaveClass('rounded')
    })

    it('applies shimmer animation classes', () => {
      render(<SkeletonLoader rows={1} variant="participant" />)

      const row = screen.getByTestId('skeleton-row-participant')
      const shimmerBlocks = row.querySelectorAll('[aria-hidden="true"]')

      shimmerBlocks.forEach((block) => {
        expect(block).toHaveClass('animate-pulse')
        expect(block).toHaveClass('bg-gradient-to-r')
        expect(block).toHaveClass('from-slate-700')
        expect(block).toHaveClass('to-slate-600')
      })
    })

    it('uses correct test id for the container', () => {
      render(<SkeletonLoader rows={2} variant="participant" />)

      const container = screen.getByTestId('skeleton-loader-participant')
      expect(container).toBeInTheDocument()
    })
  })

  describe('raise-hand variant', () => {
    it('renders the correct number of rows', () => {
      render(<SkeletonLoader rows={3} variant="raise-hand" />)

      const rows = screen.getAllByTestId('skeleton-row-raise-hand')
      expect(rows).toHaveLength(3)
    })

    it('renders icon circle and single text line per row', () => {
      render(<SkeletonLoader rows={1} variant="raise-hand" />)

      const row = screen.getByTestId('skeleton-row-raise-hand')
      const shimmerBlocks = row.querySelectorAll('[aria-hidden="true"]')

      // 1 icon circle + 1 text line = 2 shimmer blocks
      expect(shimmerBlocks).toHaveLength(2)

      // Icon circle should be round
      expect(shimmerBlocks[0]).toHaveClass('rounded-full')
      // Text line should be regular rounded
      expect(shimmerBlocks[1]).toHaveClass('rounded')
    })

    it('applies shimmer animation classes', () => {
      render(<SkeletonLoader rows={1} variant="raise-hand" />)

      const row = screen.getByTestId('skeleton-row-raise-hand')
      const shimmerBlocks = row.querySelectorAll('[aria-hidden="true"]')

      shimmerBlocks.forEach((block) => {
        expect(block).toHaveClass('animate-pulse')
        expect(block).toHaveClass('bg-gradient-to-r')
        expect(block).toHaveClass('from-slate-700')
        expect(block).toHaveClass('to-slate-600')
      })
    })

    it('uses correct test id for the container', () => {
      render(<SkeletonLoader rows={2} variant="raise-hand" />)

      const container = screen.getByTestId('skeleton-loader-raise-hand')
      expect(container).toBeInTheDocument()
    })
  })

  describe('accessibility', () => {
    it('has role="status" for loading indication', () => {
      render(<SkeletonLoader rows={2} variant="participant" />)

      const container = screen.getByTestId('skeleton-loader-participant')
      expect(container).toHaveAttribute('role', 'status')
    })

    it('has aria-label describing the loading state', () => {
      render(<SkeletonLoader rows={2} variant="raise-hand" />)

      const container = screen.getByTestId('skeleton-loader-raise-hand')
      expect(container).toHaveAttribute('aria-label', 'Loading content')
    })

    it('marks shimmer blocks as aria-hidden', () => {
      render(<SkeletonLoader rows={1} variant="participant" />)

      const row = screen.getByTestId('skeleton-row-participant')
      const shimmerBlocks = row.querySelectorAll('[aria-hidden="true"]')
      expect(shimmerBlocks.length).toBeGreaterThan(0)
    })
  })

  describe('edge cases', () => {
    it('renders zero rows when rows is 0', () => {
      render(<SkeletonLoader rows={0} variant="participant" />)

      const container = screen.getByTestId('skeleton-loader-participant')
      expect(container.children).toHaveLength(0)
    })

    it('renders a single row correctly', () => {
      render(<SkeletonLoader rows={1} variant="participant" />)

      const rows = screen.getAllByTestId('skeleton-row-participant')
      expect(rows).toHaveLength(1)
    })
  })
})
