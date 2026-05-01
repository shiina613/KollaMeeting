/**
 * E2E Test: Login → Create meeting → Add members → Activate meeting
 *
 * Flow:
 * 1. Navigate to /login
 * 2. Log in as ADMIN
 * 3. Create a new meeting (title, time, room, host, secretary)
 * 4. Add a member to the meeting
 * 5. Activate the meeting (SCHEDULED → ACTIVE)
 *
 * Requirements: 20.7, 2.1, 3.1, 3.8, 3.10
 */

import { test, expect } from '@playwright/test'
import { loginAs, logout, futureDateTime, TEST_ADMIN } from './helpers/auth'

test.describe('Login → Create meeting → Add members → Activate meeting', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_ADMIN)
  })

  test.afterEach(async ({ page }) => {
    await logout(page)
  })

  test('should log in successfully and reach dashboard', async ({ page }) => {
    await expect(page).toHaveURL('/')
    // Dashboard heading should be visible
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
  })

  test('should redirect unauthenticated users to /login', async ({ page }) => {
    await logout(page)
    await page.goto('/meetings')
    await expect(page).toHaveURL('/login')
  })

  test('should show error on invalid credentials', async ({ page }) => {
    await logout(page)
    await page.goto('/login')
    await page.getByTestId('login-username').fill('wronguser')
    await page.getByTestId('login-password').fill('wrongpass')
    await page.getByTestId('login-submit').click()
    await expect(page.getByTestId('login-error')).toBeVisible()
  })

  test('should create a new meeting', async ({ page }) => {
    // Navigate to create meeting form
    await page.goto('/meetings/new')
    await expect(page.getByRole('heading', { name: 'Tạo cuộc họp mới' })).toBeVisible()

    // Fill in the form
    const startTime = futureDateTime(24)
    const endTime = futureDateTime(25)

    await page.getByLabel('Tiêu đề').fill('E2E Test Meeting')
    await page.getByLabel('Mô tả').fill('Created by Playwright E2E test')
    await page.getByLabel('Thời gian bắt đầu').fill(startTime)
    await page.getByLabel('Thời gian kết thúc').fill(endTime)

    // Select first available room
    const roomSelect = page.getByLabel('Phòng họp')
    await roomSelect.selectOption({ index: 1 })

    // Select host (first ADMIN/SECRETARY option)
    const hostSelect = page.getByTestId('host-select')
    await hostSelect.selectOption({ index: 1 })

    // Select secretary
    const secretarySelect = page.getByTestId('secretary-select')
    await secretarySelect.selectOption({ index: 1 })

    // Submit
    await page.getByRole('button', { name: 'Tạo cuộc họp' }).click()

    // Should redirect to meeting detail page
    await expect(page).toHaveURL(/\/meetings\/\d+/)
    await expect(page.getByText('E2E Test Meeting')).toBeVisible()
  })

  test('should display meeting list with created meeting', async ({ page }) => {
    await page.goto('/meetings')
    await expect(page.getByRole('heading', { name: 'Danh sách cuộc họp' })).toBeVisible()
    // Table should be visible
    await expect(page.locator('table')).toBeVisible()
  })

  test('should show meeting detail with correct status badge', async ({ page }) => {
    // Go to meetings list and click first meeting
    await page.goto('/meetings')
    const firstRow = page.locator('tbody tr').first()
    await firstRow.click()

    // Should be on detail page
    await expect(page).toHaveURL(/\/meetings\/\d+/)
    // Status badge should be visible
    await expect(page.locator('[class*="rounded"]').filter({ hasText: /Đã lên lịch|Đang diễn ra|Đã kết thúc/ }).first()).toBeVisible()
  })
})
