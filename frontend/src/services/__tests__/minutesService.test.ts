import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import api from '../api'
import useAuthStore from '../../store/authStore'
import {
  confirmMinutes,
  downloadMinutesFile,
  getMinutesDownloadFilename,
  getMinutesDownloadUrl,
} from '../minutesService'
import type { User } from '../../types/user'

const mockAxios = new MockAdapter(api)

const mockUser: User = {
  id: 1,
  username: 'host',
  email: 'host@example.com',
  role: 'ADMIN',
}

beforeEach(() => {
  mockAxios.reset()
  useAuthStore.getState().logout()

  Object.defineProperty(URL, 'createObjectURL', {
    configurable: true,
    value: vi.fn(() => 'blob:minutes-file'),
  })
  Object.defineProperty(URL, 'revokeObjectURL', {
    configurable: true,
    value: vi.fn(),
  })
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})

afterEach(() => {
  mockAxios.reset()
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('minutesService downloads', () => {
  it('returns the signed PDF payload after Host confirmation', async () => {
    mockAxios.onPost('/meetings/42/minutes/confirm').reply(200, {
      success: true,
      message: 'Minutes confirmed successfully',
      data: {
        minutes: {
          id: 7,
          meetingId: 42,
          status: 'HOST_CONFIRMED',
          draftAvailable: true,
          confirmedAvailable: true,
          secretaryAvailable: false,
          draftDocxAvailable: true,
          secretaryDocxAvailable: false,
          createdAt: '2026-06-05T09:00:00',
          updatedAt: '2026-06-05T09:05:00',
        },
        signedPdfFileName: 'bien-ban-xac-nhan-42.pdf',
        signedPdfContentType: 'application/pdf',
        signedPdfBase64: 'JVBERi0=',
        signedPdfSha256: 'a'.repeat(64),
      },
    })

    const response = await confirmMinutes(42)

    expect(response.data.minutes.status).toBe('HOST_CONFIRMED')
    expect(response.data.signedPdfFileName).toBe('bien-ban-xac-nhan-42.pdf')
    expect(response.data.signedPdfContentType).toBe('application/pdf')
    expect(response.data.signedPdfBase64).toBe('JVBERi0=')
    expect(response.data.signedPdfSha256).toHaveLength(64)
  })

  it('builds authenticated inline PDF URLs with an explicit format', () => {
    useAuthStore.getState().login(mockToken, mockUser)

    const url = getMinutesDownloadUrl(42, 'confirmed', 'pdf')

    expect(url).toContain('/meetings/42/minutes/download')
    expect(url).toContain('version=confirmed')
    expect(url).toContain('format=pdf')
    expect(url).toContain('inline=true')
    expect(url).toContain(`token=${encodeURIComponent(mockToken)}`)
  })

  it('uses Vietnamese filenames for every version and format', () => {
    expect(getMinutesDownloadFilename(42, 'draft', 'pdf')).toBe('bien-ban-nhap-42.pdf')
    expect(getMinutesDownloadFilename(42, 'confirmed', 'docx')).toBe(
      'bien-ban-xac-nhan-42.docx',
    )
    expect(getMinutesDownloadFilename(42, 'secretary', 'docx')).toBe(
      'bien-ban-thu-ky-42.docx',
    )
  })

  it('requests DOCX downloads with format=docx and a DOCX filename', async () => {
    let capturedParams: unknown
    let appendedLink: HTMLAnchorElement | null = null

    vi.spyOn(document.body, 'appendChild').mockImplementation((node: Node) => {
      appendedLink = node as HTMLAnchorElement
      return node as HTMLElement
    })
    vi.spyOn(document.body, 'removeChild').mockImplementation((node: Node) => node as HTMLElement)

    mockAxios.onGet('/meetings/42/minutes/download').reply((config) => {
      capturedParams = config.params
      return [200, new Blob(['docx-bytes'])]
    })

    await downloadMinutesFile(42, 'secretary', 'docx')

    expect(capturedParams).toEqual({ version: 'secretary', format: 'docx' })
    expect(URL.createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(appendedLink?.download).toBe('bien-ban-thu-ky-42.docx')
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:minutes-file')
  })
})

const mockToken = 'test-jwt-token-abc123'
