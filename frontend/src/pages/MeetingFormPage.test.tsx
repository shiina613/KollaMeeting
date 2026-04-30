/**
 * Component tests for MeetingFormPage.
 * Tests: form rendering, validation, room conflict display, API calls.
 * Requirements: 20.4
 */

import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import MeetingFormPage from './MeetingFormPage'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('../services/meetingService', () => ({
  createMeeting: vi.fn(),
  updateMeeting: vi.fn(),
  getMeeting: vi.fn(),
  listRooms: vi.fn(),
  getRoomAvailability: vi.fn(),
  isSchedulingConflict: vi.fn(),
  getConflictMessage: vi.fn(),
}))

vi.mock('../services/userService', () => ({
  listHostCandidates: vi.fn(),
  listSecretaryCandidates: vi.fn(),
}))

// Import mocked modules after vi.mock declarations
import * as meetingService from '../services/meetingService'
import * as userService from '../services/userService'

// ─── Default mock data ────────────────────────────────────────────────────────

const mockRooms = [
  { id: 1, name: 'Phòng A', capacity: 10, department: { id: 1, name: 'IT' } },
]

const mockHostCandidates = [
  { id: 10, username: 'admin1', fullName: 'Admin User', email: 'admin@test.com', role: 'ADMIN' as const },
]

const mockSecretaryCandidates = [
  { id: 11, username: 'sec1', fullName: 'Secretary User', email: 'sec@test.com', role: 'SECRETARY' as const },
]

// ─── Setup default mocks ──────────────────────────────────────────────────────

function setupDefaultMocks() {
  vi.mocked(meetingService.listRooms).mockResolvedValue({
    data: mockRooms,
    success: true,
  })
  vi.mocked(meetingService.getRoomAvailability).mockResolvedValue([])
  vi.mocked(meetingService.isSchedulingConflict).mockReturnValue(false)
  vi.mocked(meetingService.getConflictMessage).mockReturnValue('')
  vi.mocked(userService.listHostCandidates).mockResolvedValue(mockHostCandidates)
  vi.mocked(userService.listSecretaryCandidates).mockResolvedValue(mockSecretaryCandidates)
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function renderCreateForm() {
  return render(
    <MemoryRouter initialEntries={['/meetings/new']}>
      <Routes>
        <Route path="/meetings/new" element={<MeetingFormPage />} />
        <Route path="/meetings/:id" element={<div>Meeting Detail</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

// ─── Test 1: Renders form fields correctly ────────────────────────────────────

describe('MeetingFormPage — renders form fields', () => {
  beforeEach(() => {
    setupDefaultMocks()
  })

  it('renders all required form fields', async () => {
    renderCreateForm()

    await waitFor(() => {
      expect(screen.getByLabelText(/tiêu đề/i)).toBeInTheDocument()
    })

    expect(screen.getByLabelText(/mô tả/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/thời gian bắt đầu/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/thời gian kết thúc/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/phòng họp/i)).toBeInTheDocument()
    expect(screen.getByTestId('host-select')).toBeInTheDocument()
    expect(screen.getByTestId('secretary-select')).toBeInTheDocument()
  })

  it('renders create heading', async () => {
    renderCreateForm()

    await waitFor(() => {
      expect(screen.getByText('Tạo cuộc họp mới')).toBeInTheDocument()
    })
  })

  it('renders submit button with correct label for create', async () => {
    renderCreateForm()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /tạo cuộc họp/i })).toBeInTheDocument()
    })
  })

  it('populates room dropdown from API', async () => {
    renderCreateForm()

    // The room option text includes capacity and department, so use a partial match
    await waitFor(() => {
      const roomSelect = screen.getByLabelText(/phòng họp/i)
      expect(roomSelect).toBeInTheDocument()
      // Check that the option with value "1" exists
      const option = roomSelect.querySelector('option[value="1"]')
      expect(option).toBeInTheDocument()
      expect(option?.textContent).toContain('Phòng A')
    })
  })
})

// ─── Test 2: Shows validation errors when required fields are empty ───────────

describe('MeetingFormPage — validation errors on empty submit', () => {
  beforeEach(() => {
    vi.mocked(meetingService.listRooms).mockResolvedValue({ data: [], success: true })
    vi.mocked(meetingService.getRoomAvailability).mockResolvedValue([])
    vi.mocked(meetingService.isSchedulingConflict).mockReturnValue(false)
    vi.mocked(meetingService.getConflictMessage).mockReturnValue('')
    vi.mocked(userService.listHostCandidates).mockResolvedValue([])
    vi.mocked(userService.listSecretaryCandidates).mockResolvedValue([])
  })

  it('shows all required field errors when submitting empty form', async () => {
    renderCreateForm()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /tạo cuộc họp/i })).toBeInTheDocument()
    })

    await userEvent.click(screen.getByRole('button', { name: /tạo cuộc họp/i }))

    await waitFor(() => {
      expect(screen.getByText('Tiêu đề là bắt buộc')).toBeInTheDocument()
    })

    expect(screen.getByText('Thời gian bắt đầu là bắt buộc')).toBeInTheDocument()
    expect(screen.getByText('Thời gian kết thúc là bắt buộc')).toBeInTheDocument()
    expect(screen.getByText('Phòng họp là bắt buộc')).toBeInTheDocument()
    expect(screen.getByText('Chủ trì là bắt buộc')).toBeInTheDocument()
    expect(screen.getByText('Thư ký là bắt buộc')).toBeInTheDocument()
  })

  it('does not call createMeeting when form is invalid', async () => {
    renderCreateForm()

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /tạo cuộc họp/i })).toBeInTheDocument()
    })

    await userEvent.click(screen.getByRole('button', { name: /tạo cuộc họp/i }))

    expect(vi.mocked(meetingService.createMeeting)).not.toHaveBeenCalled()
  })
})

// ─── Test 3: Shows validation error when endTime <= startTime ─────────────────

describe('MeetingFormPage — endTime validation', () => {
  beforeEach(() => {
    vi.mocked(meetingService.listRooms).mockResolvedValue({ data: [], success: true })
    vi.mocked(meetingService.getRoomAvailability).mockResolvedValue([])
    vi.mocked(meetingService.isSchedulingConflict).mockReturnValue(false)
    vi.mocked(meetingService.getConflictMessage).mockReturnValue('')
    vi.mocked(userService.listHostCandidates).mockResolvedValue([])
    vi.mocked(userService.listSecretaryCandidates).mockResolvedValue([])
  })

  it('shows error when endTime is before startTime', async () => {
    renderCreateForm()

    await waitFor(() => {
      expect(screen.getByLabelText(/tiêu đề/i)).toBeInTheDocument()
    })

    await userEvent.type(screen.getByLabelText(/tiêu đề/i), 'Test Meeting')

    fireEvent.change(screen.getByLabelText(/thời gian bắt đầu/i), {
      target: { value: '2030-12-01T10:00' },
    })
    fireEvent.change(screen.getByLabelText(/thời gian kết thúc/i), {
      target: { value: '2030-12-01T09:00' },
    })

    await userEvent.click(screen.getByRole('button', { name: /tạo cuộc họp/i }))

    await waitFor(() => {
      expect(
        screen.getByText('Thời gian kết thúc phải sau thời gian bắt đầu'),
      ).toBeInTheDocument()
    })
  })

  it('shows error when endTime equals startTime', async () => {
    renderCreateForm()

    await waitFor(() => {
      expect(screen.getByLabelText(/tiêu đề/i)).toBeInTheDocument()
    })

    await userEvent.type(screen.getByLabelText(/tiêu đề/i), 'Test Meeting')

    fireEvent.change(screen.getByLabelText(/thời gian bắt đầu/i), {
      target: { value: '2030-12-01T10:00' },
    })
    fireEvent.change(screen.getByLabelText(/thời gian kết thúc/i), {
      target: { value: '2030-12-01T10:00' },
    })

    await userEvent.click(screen.getByRole('button', { name: /tạo cuộc họp/i }))

    await waitFor(() => {
      expect(
        screen.getByText('Thời gian kết thúc phải sau thời gian bắt đầu'),
      ).toBeInTheDocument()
    })
  })
})

// ─── Test 4: Calls createMeeting API on valid submit ─────────────────────────

describe('MeetingFormPage — calls createMeeting on valid submit', () => {
  beforeEach(() => {
    setupDefaultMocks()

    vi.mocked(meetingService.createMeeting).mockResolvedValue({
      data: {
        id: 99,
        title: 'Test Meeting',
        meetingCode: 'ABC123',
        status: 'SCHEDULED',
        mode: 'FREE_MODE',
        transcriptionPriority: 'NORMAL_PRIORITY',
        startTime: '2030-12-01T03:00:00Z',
        endTime: '2030-12-01T04:00:00Z',
        room: { id: 1, name: 'Phòng A', department: { id: 1, name: 'IT' } },
        hostUser: { id: 10, username: 'admin1', fullName: 'Admin User', email: 'admin@test.com', role: 'ADMIN' },
        secretaryUser: { id: 11, username: 'sec1', fullName: 'Secretary User', email: 'sec@test.com', role: 'SECRETARY' },
        createdBy: { id: 10, username: 'admin1', fullName: 'Admin User', email: 'admin@test.com', role: 'ADMIN' },
      },
      success: true,
    })
  })

  it('calls createMeeting with correct payload on valid submit', async () => {
    renderCreateForm()

    // Wait for dropdowns to populate
    await waitFor(() => {
      const roomSelect = screen.getByLabelText(/phòng họp/i)
      const option = roomSelect.querySelector('option[value="1"]')
      expect(option).toBeInTheDocument()
    })

    // Fill in the form
    await userEvent.type(screen.getByLabelText(/tiêu đề/i), 'Test Meeting')

    fireEvent.change(screen.getByLabelText(/thời gian bắt đầu/i), {
      target: { value: '2030-12-01T10:00' },
    })
    fireEvent.change(screen.getByLabelText(/thời gian kết thúc/i), {
      target: { value: '2030-12-01T11:00' },
    })

    fireEvent.change(screen.getByLabelText(/phòng họp/i), {
      target: { value: '1' },
    })

    fireEvent.change(screen.getByTestId('host-select'), {
      target: { value: '10' },
    })

    fireEvent.change(screen.getByTestId('secretary-select'), {
      target: { value: '11' },
    })

    await userEvent.click(screen.getByRole('button', { name: /tạo cuộc họp/i }))

    await waitFor(() => {
      expect(vi.mocked(meetingService.createMeeting)).toHaveBeenCalledOnce()
    })

    const callArgs = vi.mocked(meetingService.createMeeting).mock.calls[0][0]
    expect(callArgs.title).toBe('Test Meeting')
    expect(callArgs.roomId).toBe(1)
    expect(callArgs.hostUserId).toBe(10)
    expect(callArgs.secretaryUserId).toBe(11)
    // startTime and endTime should be ISO strings
    expect(callArgs.startTime).toMatch(/^\d{4}-\d{2}-\d{2}T/)
    expect(callArgs.endTime).toMatch(/^\d{4}-\d{2}-\d{2}T/)
  })
})

// ─── Test 5: Shows room conflict warning when room is already booked ──────────

describe('MeetingFormPage — room conflict display', () => {
  it('shows room conflict warning when room is already booked', async () => {
    vi.mocked(meetingService.listRooms).mockResolvedValue({
      data: [{ id: 1, name: 'Phòng A', capacity: 10, department: { id: 1, name: 'IT' } }],
      success: true,
    })
    // Return a conflict slot
    vi.mocked(meetingService.getRoomAvailability).mockResolvedValue([
      {
        startTime: '2030-12-01T03:00:00Z',
        endTime: '2030-12-01T04:00:00Z',
        meetingId: 5,
        meetingTitle: 'Cuộc họp đã có',
      },
    ])
    vi.mocked(meetingService.isSchedulingConflict).mockReturnValue(false)
    vi.mocked(meetingService.getConflictMessage).mockReturnValue('')
    vi.mocked(userService.listHostCandidates).mockResolvedValue([])
    vi.mocked(userService.listSecretaryCandidates).mockResolvedValue([])

    renderCreateForm()

    // Wait for room dropdown to populate
    await waitFor(() => {
      const roomSelect = screen.getByLabelText(/phòng họp/i)
      const option = roomSelect.querySelector('option[value="1"]')
      expect(option).toBeInTheDocument()
    })

    // Set valid start and end times
    fireEvent.change(screen.getByLabelText(/thời gian bắt đầu/i), {
      target: { value: '2030-12-01T10:00' },
    })
    fireEvent.change(screen.getByLabelText(/thời gian kết thúc/i), {
      target: { value: '2030-12-01T11:00' },
    })

    // Select room — triggers availability check
    fireEvent.change(screen.getByLabelText(/phòng họp/i), {
      target: { value: '1' },
    })

    // Wait for conflict indicator to appear
    await waitFor(() => {
      expect(screen.getByTestId('room-conflict-indicator')).toBeInTheDocument()
    })

    expect(screen.getByText('Cuộc họp đã có')).toBeInTheDocument()
  })

  it('shows room available indicator when no conflicts', async () => {
    vi.mocked(meetingService.listRooms).mockResolvedValue({
      data: [{ id: 1, name: 'Phòng A', capacity: 10, department: { id: 1, name: 'IT' } }],
      success: true,
    })
    vi.mocked(meetingService.getRoomAvailability).mockResolvedValue([])
    vi.mocked(meetingService.isSchedulingConflict).mockReturnValue(false)
    vi.mocked(meetingService.getConflictMessage).mockReturnValue('')
    vi.mocked(userService.listHostCandidates).mockResolvedValue([])
    vi.mocked(userService.listSecretaryCandidates).mockResolvedValue([])

    renderCreateForm()

    await waitFor(() => {
      const roomSelect = screen.getByLabelText(/phòng họp/i)
      const option = roomSelect.querySelector('option[value="1"]')
      expect(option).toBeInTheDocument()
    })

    fireEvent.change(screen.getByLabelText(/thời gian bắt đầu/i), {
      target: { value: '2030-12-01T10:00' },
    })
    fireEvent.change(screen.getByLabelText(/thời gian kết thúc/i), {
      target: { value: '2030-12-01T11:00' },
    })
    fireEvent.change(screen.getByLabelText(/phòng họp/i), {
      target: { value: '1' },
    })

    await waitFor(() => {
      expect(screen.getByTestId('room-available-indicator')).toBeInTheDocument()
    })
  })
})
