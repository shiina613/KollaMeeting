import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MinutesDownloadButtons from './MinutesDownloadButtons'
import { downloadMinutesFile } from '../../services/minutesService'
import type { Minutes } from '../../types/minutes'

vi.mock('../../services/minutesService', () => ({
  downloadMinutesFile: vi.fn(),
}))

const minutes: Minutes = {
  id: 1,
  meetingId: 42,
  status: 'HOST_CONFIRMED',
  draftAvailable: true,
  confirmedAvailable: true,
  secretaryAvailable: false,
  draftDocxAvailable: true,
  secretaryDocxAvailable: false,
  createdAt: '2026-06-05T08:00:00',
  updatedAt: '2026-06-05T08:00:00',
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(downloadMinutesFile).mockResolvedValue()
})

describe('MinutesDownloadButtons', () => {
  it('renders PDF and Word actions for each minutes version', () => {
    render(<MinutesDownloadButtons meetingId={42} minutes={minutes} />)

    expect(screen.getByTestId('download-btn-draft-pdf')).toBeEnabled()
    expect(screen.getByTestId('download-btn-draft-docx')).toBeEnabled()
    expect(screen.getByTestId('download-btn-confirmed-pdf')).toBeEnabled()
    expect(screen.getByTestId('download-btn-confirmed-docx')).toBeEnabled()
    expect(screen.getByTestId('download-btn-secretary-pdf')).toBeDisabled()
    expect(screen.getByTestId('download-btn-secretary-docx')).toBeDisabled()
  })

  it('downloads the requested Word version when a DOCX button is clicked', async () => {
    const user = userEvent.setup()
    render(<MinutesDownloadButtons meetingId={42} minutes={minutes} />)

    await user.click(screen.getByTestId('download-btn-confirmed-docx'))

    await waitFor(() => {
      expect(downloadMinutesFile).toHaveBeenCalledWith(42, 'confirmed', 'docx')
    })
  })
})
