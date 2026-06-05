import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import MockAdapter from 'axios-mock-adapter'
import api from '../api'
import useAuthStore from '../../store/authStore'
import {
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
