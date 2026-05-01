/**
 * E2E test helpers — authentication and common actions.
 */

import type { Page } from '@playwright/test'

export const TEST_ADMIN = {
  username: process.env.E2E_ADMIN_USERNAME ?? 'admin',
  password: process.env.E2E_ADMIN_PASSWORD ?? 'admin123',
}

export const TEST_SECRETARY = {
  username: process.env.E2E_SECRETARY_USERNAME ?? 'secretary',
  password: process.env.E2E_SECRETARY_PASSWORD ?? 'secretary123',
}

export const TEST_USER = {
  username: process.env.E2E_USER_USERNAME ?? 'user',
  password: process.env.E2E_USER_PASSWORD ?? 'user123',
}

/**
 * Log in via the login page UI.
 */
export async function loginAs(
  page: Page,
  credentials: { username: string; password: string },
): Promise<void> {
  await page.goto('/login')
  await page.getByTestId('login-username').fill(credentials.username)
  await page.getByTestId('login-password').fill(credentials.password)
  await page.getByTestId('login-submit').click()
  // Wait for redirect to dashboard
  await page.waitForURL('/', { timeout: 10_000 })
}

/**
 * Log out via the sidebar logout button.
 */
export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: 'Đăng xuất' }).click()
  await page.waitForURL('/login', { timeout: 5_000 })
}

/**
 * Format a datetime-local input value (UTC+7) for use in date inputs.
 * Returns "YYYY-MM-DDTHH:mm" string.
 */
export function futureDateTime(offsetHours = 24): string {
  const d = new Date(Date.now() + offsetHours * 3_600_000)
  // Format in UTC+7
  const formatter = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Ho_Chi_Minh',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
  return formatter.format(d).replace(' ', 'T')
}
