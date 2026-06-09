import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SearchPage from './SearchPage'

const searchServiceMocks = vi.hoisted(() => ({
  searchMeetings: vi.fn(),
  searchTranscriptions: vi.fn(),
}))

vi.mock('../services/searchService', () => searchServiceMocks)

vi.mock('../services/meetingService', () => ({
  listRooms: vi.fn().mockResolvedValue({ data: [] }),
  listDepartments: vi.fn().mockResolvedValue({ data: [] }),
}))

function renderPage() {
  return render(
    <MemoryRouter>
      <SearchPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('SearchPage meeting results', () => {
  it('shows only meeting titles in the meeting search result list', async () => {
    const user = userEvent.setup()
    searchServiceMocks.searchMeetings.mockResolvedValue({
      success: true,
      data: {
        content: [
          {
            id: 1,
            title: 'Hop hoi dong',
            meetingCode: 'MTG-001',
            status: 'SCHEDULED',
            mode: 'FREE_MODE',
            transcriptionPriority: 'NORMAL_PRIORITY',
            startTime: '2026-06-09T09:00:00',
            endTime: '2026-06-09T10:00:00',
            roomName: 'Phong A',
          },
        ],
        totalElements: 1,
        totalPages: 1,
        size: 10,
        number: 0,
      },
    })

    renderPage()

    await user.type(screen.getByTestId('search-input'), 'phan bien')
    await user.click(screen.getByTestId('search-submit'))

    await waitFor(() => {
      expect(searchServiceMocks.searchMeetings).toHaveBeenCalledWith(
        expect.objectContaining({ query: 'phan bien', page: 0, size: 10 }),
      )
    })

    const results = await screen.findByTestId('meeting-results')
    expect(results).toHaveTextContent('Hop hoi dong')
    expect(results).not.toHaveTextContent('Phong A')
    expect(results).not.toHaveTextContent('09/06/2026')
  })
})
