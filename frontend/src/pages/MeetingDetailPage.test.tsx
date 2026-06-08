import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MeetingDetailPage from './MeetingDetailPage'
import api from '../services/api'

vi.mock('../store/authStore', () => ({
  default: () => ({
    user: { id: 10, role: 'SECRETARY', fullName: 'Thư ký A', username: 'sec01' },
  }),
}))

vi.mock('../services/recordingService', () => ({
  formatFileSize: (bytes: number) => `${bytes} B`,
  listRecordings: vi.fn().mockResolvedValue({ data: [] }),
  triggerRecordingDownload: vi.fn(),
}))

vi.mock('../services/minutesService', () => ({
  getMinutes: vi.fn().mockResolvedValue({ data: null }),
}))

const apiMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('../services/api', () => ({ default: apiMocks }))

const meetingServiceMocks = vi.hoisted(() => ({
  getMeeting: vi.fn(),
  listMeetingMembers: vi.fn(),
  addMeetingMember: vi.fn(),
  removeMeetingMember: vi.fn(),
  activateMeeting: vi.fn(),
  endMeeting: vi.fn(),
  listAudioJobs: vi.fn(),
  fetchAudioJobBlob: vi.fn(),
  listMeetingMessages: vi.fn(),
  createMeetingMessage: vi.fn(),
}))

vi.mock('../services/meetingService', () => meetingServiceMocks)

const userServiceMocks = vi.hoisted(() => ({
  listAllActiveUsers: vi.fn(),
}))

vi.mock('../services/userService', () => userServiceMocks)

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/meetings/1"]}>
      <Routes>
        <Route path="/meetings/:id" element={<MeetingDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function mockApiDocs(docs: unknown[] = []) {
  vi.mocked(api.get).mockImplementation((url: string) => {
    if (url === '/meetings/1/documents') {
      return Promise.resolve({ data: { data: docs } })
    }
    return Promise.resolve({ data: { data: [] } })
  })
}

function meetingData(overrides = {}) {
  return {
    id: 1,
    title: 'Họp hội đồng',
    meetingCode: 'MTG-001',
    status: 'SCHEDULED',
    mode: 'FREE_MODE',
    transcriptionPriority: 'NORMAL_PRIORITY',
    startTime: '2026-06-09T09:00:00',
    endTime: '2026-06-09T10:00:00',
    hostId: 20,
    hostName: 'Chủ trì B',
    secretaryId: 10,
    secretaryName: 'Thư ký A',
    roomName: 'Phòng họp 1',
    ...overrides,
  }
}

beforeEach(() => {
  vi.clearAllMocks()
  meetingServiceMocks.getMeeting.mockResolvedValue({ data: meetingData() })
  meetingServiceMocks.listMeetingMembers.mockResolvedValue({
    data: [
      { id: 100, userId: 20, username: 'host01', fullName: 'Chủ trì B', meetingRole: 'HOST' },
      { id: 101, userId: 10, username: 'sec01', fullName: 'Thư ký A', meetingRole: 'SECRETARY' },
    ],
  })
  meetingServiceMocks.listMeetingMessages.mockResolvedValue({ data: [] })
  meetingServiceMocks.listAudioJobs.mockResolvedValue({ data: [] })
  meetingServiceMocks.addMeetingMember.mockResolvedValue({ data: undefined })
  meetingServiceMocks.removeMeetingMember.mockResolvedValue({ data: undefined })
  userServiceMocks.listAllActiveUsers.mockResolvedValue([
    { id: 30, username: 'reviewer01', fullName: 'Phản biện C', email: 'c@example.com', role: 'USER', isActive: true },
  ])
  mockApiDocs()
})

describe('MeetingDetailPage member role picker', () => {
  it('uses MEMBER as the default role when adding a member from detail page', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Họp hội đồng')
    await user.click(screen.getByRole('tab', { name: /Thành viên/i }))
    await user.click(screen.getByRole('button', { name: /Thêm \/ bỏ thành viên/i }))
    await user.click(await screen.findByRole('button', { name: /Phản biện C/i }))

    await waitFor(() => {
      expect(meetingServiceMocks.addMeetingMember).toHaveBeenCalledWith(1, 30, 'MEMBER')
    })
  })

  it('passes selected meetingRole when adding a member from detail page', async () => {
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Họp hội đồng')
    await user.click(screen.getByRole('tab', { name: /Thành viên/i }))
    await user.click(screen.getByRole('button', { name: /Thêm \/ bỏ thành viên/i }))
    await user.selectOptions(await screen.findByLabelText(/Vai trò khi thêm/i), 'REVIEWER')
    await user.click(await screen.findByRole('button', { name: /Phản biện C/i }))

    await waitFor(() => {
      expect(meetingServiceMocks.addMeetingMember).toHaveBeenCalledWith(1, 30, 'REVIEWER')
    })
  })
})

describe('MeetingDetailPage document permissions', () => {
  const docs = [{ id: 501, fileName: 'agenda.pdf', fileSize: 100, uploadedAt: '2026-06-08T08:00:00' }]

  it('shows document delete button for assigned secretary', async () => {
    mockApiDocs(docs)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Họp hội đồng')
    await user.click(screen.getByRole('tab', { name: /Tài liệu/i }))

    expect(await screen.findByRole('button', { name: /Xóa agenda\.pdf/i })).toBeInTheDocument()
  })

  it('hides document delete button for secretary not assigned to meeting', async () => {
    meetingServiceMocks.getMeeting.mockResolvedValueOnce({ data: meetingData({ secretaryId: 99, secretaryName: 'Thư ký khác' }) })
    mockApiDocs(docs)
    const user = userEvent.setup()
    renderPage()

    await screen.findByText('Họp hội đồng')
    await user.click(screen.getByRole('tab', { name: /Tài liệu/i }))

    await screen.findByText('agenda.pdf')
    expect(screen.queryByRole('button', { name: /Xóa agenda\.pdf/i })).not.toBeInTheDocument()
  })
})
