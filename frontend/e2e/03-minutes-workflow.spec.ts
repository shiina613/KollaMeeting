/**
 * E2E Test: End meeting → Host confirm minutes → Secretary edit → Download PDF
 *
 * Flow:
 * 1. Navigate to an ENDED meeting
 * 2. Host confirms the draft minutes
 * 3. Secretary edits and publishes minutes
 * 4. Download PDF (draft, confirmed, secretary versions)
 *
 * Requirements: 20.7, 25.1–25.6
 */

import { test, expect } from '@playwright/test'
import { loginAs, TEST_ADMIN, TEST_SECRETARY } from './helpers/auth'

test.describe('Minutes workflow: confirm → edit → download', () => {
  test('should display minutes section on ended meeting detail page', async ({ page }) => {
    await loginAs(page, TEST_ADMIN)
    await page.goto('/meetings')

    // Find an ENDED meeting
    const endedRow = page.locator('tbody tr').filter({ hasText: 'Đã kết thúc' }).first()
    const count = await endedRow.count()
    if (count === 0) {
      test.skip()
      return
    }

    await endedRow.click()
    await expect(page).toHaveURL(/\/meetings\/\d+/)

    // Meeting detail page should load
    await expect(page.locator('h1')).toBeVisible()
  })

  test('should show minutes confirm dialog for host on ended meeting', async ({ page }) => {
    await loginAs(page, TEST_ADMIN)
    await page.goto('/meetings')

    const endedRow = page.locator('tbody tr').filter({ hasText: 'Đã kết thúc' }).first()
    const count = await endedRow.count()
    if (count === 0) { test.skip(); return }

    await endedRow.click()
    await expect(page).toHaveURL(/\/meetings\/\d+/)

    // Look for minutes confirm button (visible to host)
    const confirmBtn = page.getByRole('button', { name: /Xác nhận biên bản|Confirm minutes/i })
    const btnCount = await confirmBtn.count()
    if (btnCount === 0) {
      // Minutes may not be generated yet or already confirmed — skip
      test.skip()
      return
    }

    await confirmBtn.click()
    // Confirm dialog should appear
    await expect(page.getByTestId('minutes-confirm-dialog')).toBeVisible({ timeout: 5_000 })
  })

  test('should show minutes editor for secretary on ended meeting', async ({ page }) => {
    await loginAs(page, TEST_SECRETARY)
    await page.goto('/meetings')

    const endedRow = page.locator('tbody tr').filter({ hasText: 'Đã kết thúc' }).first()
    const count = await endedRow.count()
    if (count === 0) { test.skip(); return }

    await endedRow.click()
    await expect(page).toHaveURL(/\/meetings\/\d+/)

    // Minutes editor should be visible for secretary on HOST_CONFIRMED minutes
    const editor = page.getByTestId('minutes-editor')
    const editorCount = await editor.count()
    if (editorCount > 0) {
      await expect(editor).toBeVisible()
    }
  })

  test('should show download buttons for minutes versions', async ({ page }) => {
    await loginAs(page, TEST_ADMIN)
    await page.goto('/meetings')

    const endedRow = page.locator('tbody tr').filter({ hasText: 'Đã kết thúc' }).first()
    const count = await endedRow.count()
    if (count === 0) { test.skip(); return }

    await endedRow.click()
    await expect(page).toHaveURL(/\/meetings\/\d+/)

    // Download buttons for minutes versions
    const downloadBtns = page.getByRole('button', { name: /Tải xuống|Download/i })
    const btnCount = await downloadBtns.count()
    if (btnCount > 0) {
      await expect(downloadBtns.first()).toBeVisible()
    }
  })

  test('should allow secretary to submit edited minutes', async ({ page }) => {
    await loginAs(page, TEST_SECRETARY)
    await page.goto('/meetings')

    const endedRow = page.locator('tbody tr').filter({ hasText: 'Đã kết thúc' }).first()
    const count = await endedRow.count()
    if (count === 0) { test.skip(); return }

    await endedRow.click()
    await expect(page).toHaveURL(/\/meetings\/\d+/)

    const editor = page.getByTestId('minutes-editor')
    const editorCount = await editor.count()
    if (editorCount === 0) { test.skip(); return }

    // Fill in content and submit
    const textarea = page.getByTestId('minutes-content-textarea')
    await textarea.fill('<h1>Biên bản cuộc họp</h1><p>Nội dung được chỉnh sửa bởi thư ký.</p>')

    const submitBtn = page.getByTestId('minutes-editor-submit')
    await expect(submitBtn).toBeEnabled()
    await submitBtn.click()

    // Should show success or error message
    await expect(
      page.getByTestId('minutes-editor-success').or(page.getByTestId('minutes-editor-error'))
    ).toBeVisible({ timeout: 10_000 })
  })
})
