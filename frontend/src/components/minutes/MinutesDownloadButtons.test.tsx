import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import MinutesDownloadButtons from './MinutesDownloadButtons'
import { downloadMinutesFile } from '../../services/minutesService'
import type { Minutes } from '../../types/minutes'

vi.mock('../../services/minutesService', () => ({ downloadMinutesFile: vi.fn() }))

const rawMinutes: Minutes = {
  id: 1,
  meetingId: 42,
  status: 'HOST_CONFIRMED',
  draftAvailable: true,
  confirmedAvailable: true,
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
  it('shows raw signed PDF and raw Word before edited Word exists', () => {
    render(<MinutesDownloadButtons meetingId={42} minutes={rawMinutes} />)
    expect(screen.getByTestId('download-btn-raw-signed-pdf')).toBeEnabled()
    expect(screen.getByTestId('download-btn-raw-word')).toBeEnabled()
    expect(screen.queryByTestId('download-btn-edited-word')).not.toBeInTheDocument()
  })

  it('shows edited Word only after edited Word exists', () => {
    render(<MinutesDownloadButtons meetingId={42} minutes={{ ...rawMinutes, secretaryDocxAvailable: true, editedWordAvailable: true }} />)
    expect(screen.getByTestId('download-btn-edited-word')).toBeEnabled()
    expect(screen.queryByTestId('download-btn-raw-signed-pdf')).not.toBeInTheDocument()
  })

  it('downloads raw Word when clicked', async () => {
    const user = userEvent.setup()
    render(<MinutesDownloadButtons meetingId={42} minutes={rawMinutes} />)
    await user.click(screen.getByTestId('download-btn-raw-word'))
    await waitFor(() => expect(downloadMinutesFile).toHaveBeenCalledWith(42, 'draft', 'docx'))
  })
})
