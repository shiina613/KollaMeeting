/**
 * Unit tests for JitsiFrame component.
 *
 * Tests:
 * - When VITE_JAAS_APP_ID is set: domain is 8x8.vc, script src is https://8x8.vc/external_api.js
 * - When VITE_JAAS_APP_ID is not set: domain is VITE_JITSI_URL, fallback behavior unchanged
 * - When script fails to load: shows error message with actual domain
 *
 * Requirements: 3.2, 3.4, 3.5, 3.6
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import React from 'react'

// ─── Helpers ──────────────────────────────────────────────────────────────────

/**
 * Simulate a script load or error event on the injected script tag.
 */
function triggerScriptEvent(eventType: 'load' | 'error') {
  const script = document.getElementById('jitsi-external-api') as HTMLScriptElement | null
  if (!script) throw new Error('Script tag not found in document')
  script.dispatchEvent(new Event(eventType))
}

/**
 * Get the script tag injected by JitsiFrame.
 */
function getInjectedScript(): HTMLScriptElement | null {
  return document.getElementById('jitsi-external-api') as HTMLScriptElement | null
}

// ─── Cleanup ──────────────────────────────────────────────────────────────────

beforeEach(() => {
  // Remove any previously injected script tags between tests
  const existing = document.getElementById('jitsi-external-api')
  if (existing) existing.remove()

  // Reset modules so module-level constants are re-evaluated with new env
  vi.resetModules()
})

afterEach(() => {
  vi.unstubAllEnvs()
  const existing = document.getElementById('jitsi-external-api')
  if (existing) existing.remove()
})

// ─── Test Suite 1: JaaS enabled (VITE_JAAS_APP_ID is set) ────────────────────

describe('JitsiFrame — JaaS enabled (VITE_JAAS_APP_ID set)', () => {
  it('injects script with src https://8x8.vc/external_api.js', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="vpaas-magic-cookie-abc123/TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    const script = getInjectedScript()
    expect(script).not.toBeNull()
    expect(script!.src).toBe('https://8x8.vc/external_api.js')
  })

  it('uses domain 8x8.vc when initializing JitsiMeetExternalAPI', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const mockApiInstance = {
      executeCommand: vi.fn(),
      dispose: vi.fn(),
      addEventListeners: vi.fn(),
    }
    const MockJitsiAPI = vi.fn(() => mockApiInstance)
    window.JitsiMeetExternalAPI = MockJitsiAPI as unknown as typeof window.JitsiMeetExternalAPI

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="vpaas-magic-cookie-abc123/TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    // Simulate script load to trigger API initialization
    await act(async () => {
      triggerScriptEvent('load')
    })

    expect(MockJitsiAPI).toHaveBeenCalledWith('8x8.vc', expect.any(Object))

    // Cleanup
    delete window.JitsiMeetExternalAPI
  })

  it('shows error message with 8x8.vc domain when script fails to load', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="vpaas-magic-cookie-abc123/TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    await act(async () => {
      triggerScriptEvent('error')
    })

    expect(screen.getByText(/Kiểm tra kết nối đến/)).toBeInTheDocument()
    expect(screen.getByText(/8x8\.vc/)).toBeInTheDocument()
  })
})

// ─── Test Suite 2: JaaS disabled (VITE_JAAS_APP_ID not set) ──────────────────

describe('JitsiFrame — JaaS disabled (VITE_JAAS_APP_ID not set)', () => {
  it('injects script with src from VITE_JITSI_URL', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    const script = getInjectedScript()
    expect(script).not.toBeNull()
    expect(script!.src).toBe('https://meet.jit.si/external_api.js')
  })

  it('uses JITSI_DOMAIN (from VITE_JITSI_URL) when initializing JitsiMeetExternalAPI', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const mockApiInstance = {
      executeCommand: vi.fn(),
      dispose: vi.fn(),
      addEventListeners: vi.fn(),
    }
    const MockJitsiAPI = vi.fn(() => mockApiInstance)
    window.JitsiMeetExternalAPI = MockJitsiAPI as unknown as typeof window.JitsiMeetExternalAPI

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    await act(async () => {
      triggerScriptEvent('load')
    })

    expect(MockJitsiAPI).toHaveBeenCalledWith('meet.jit.si', expect.any(Object))

    delete window.JitsiMeetExternalAPI
  })

  it('shows error message with JITSI_URL when script fails to load', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    await act(async () => {
      triggerScriptEvent('error')
    })

    expect(screen.getByText(/Kiểm tra kết nối đến/)).toBeInTheDocument()
    expect(screen.getByText(/meet\.jit\.si/)).toBeInTheDocument()
  })

  it('uses custom VITE_JITSI_URL when set', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')
    vi.stubEnv('VITE_JITSI_URL', 'https://jitsi.mycompany.com')

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    const script = getInjectedScript()
    expect(script).not.toBeNull()
    expect(script!.src).toBe('https://jitsi.mycompany.com/external_api.js')
  })
})

// ─── Test Suite 3: JWT prop passthrough ──────────────────────────────────────

describe('JitsiFrame — JWT prop passthrough', () => {
  it('passes jwt option to JitsiMeetExternalAPI when jwt prop is provided', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', 'vpaas-magic-cookie-abc123')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const mockApiInstance = {
      executeCommand: vi.fn(),
      dispose: vi.fn(),
      addEventListeners: vi.fn(),
    }
    const MockJitsiAPI = vi.fn(() => mockApiInstance)
    window.JitsiMeetExternalAPI = MockJitsiAPI as unknown as typeof window.JitsiMeetExternalAPI

    const { default: JitsiFrame } = await import('./JitsiFrame')
    const testJwt = 'eyJhbGciOiJSUzI1NiJ9.test.signature'

    render(
      <JitsiFrame
        meetingCode="vpaas-magic-cookie-abc123/TESTCODE1234567890AB"
        displayName="Test User"
        jwt={testJwt}
      />,
    )

    await act(async () => {
      triggerScriptEvent('load')
    })

    expect(MockJitsiAPI).toHaveBeenCalledWith(
      '8x8.vc',
      expect.objectContaining({ jwt: testJwt }),
    )

    delete window.JitsiMeetExternalAPI
  })

  it('does NOT pass jwt option when jwt prop is not provided', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const mockApiInstance = {
      executeCommand: vi.fn(),
      dispose: vi.fn(),
      addEventListeners: vi.fn(),
    }
    const MockJitsiAPI = vi.fn(() => mockApiInstance)
    window.JitsiMeetExternalAPI = MockJitsiAPI as unknown as typeof window.JitsiMeetExternalAPI

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    await act(async () => {
      triggerScriptEvent('load')
    })

    const callOptions = MockJitsiAPI.mock.calls[0]?.[1] as Record<string, unknown>
    expect(callOptions).not.toHaveProperty('jwt')

    delete window.JitsiMeetExternalAPI
  })
})

// ─── Test Suite 4: Loading state ─────────────────────────────────────────────

describe('JitsiFrame — loading state', () => {
  it('shows loading overlay before script is loaded', async () => {
    vi.stubEnv('VITE_JAAS_APP_ID', '')
    vi.stubEnv('VITE_JITSI_URL', 'https://meet.jit.si')

    const { default: JitsiFrame } = await import('./JitsiFrame')

    render(
      <JitsiFrame
        meetingCode="TESTCODE1234567890AB"
        displayName="Test User"
      />,
    )

    // Before script loads, loading overlay should be visible
    expect(screen.getByText('Đang kết nối phòng họp...')).toBeInTheDocument()
  })
})
