import { expect, test } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { signUpViaApi } from '../support/api'
import { makeAdmin } from '../support/db'
import { signIn } from '../support/ui'

test('a user reports a problem and an admin resolves it', async ({ browser }) => {
  const [user, admin] = await Promise.all([signUpViaApi('reporter'), signUpViaApi('admin')])
  await makeAdmin(admin.email)
  const text = `Charged twice, e2e ${randomUUID()}`

  const userPage = await (await browser.newContext()).newPage()
  await signIn(userPage, user)
  await userPage.goto('/help/report')
  await userPage.getByLabel('What went wrong?').selectOption({ index: 1 })
  await userPage.getByLabel('Tell us what happened').fill(text)
  await userPage.getByRole('button', { name: 'Send report' }).click()
  await expect(userPage.getByRole('heading', { name: 'Thanks, we got your report.' })).toBeVisible()

  const adminPage = await (await browser.newContext()).newPage()
  await signIn(adminPage, admin)
  await adminPage.goto('/admin/reports')
  const report = adminPage.getByRole('listitem').filter({ hasText: text })
  await expect(report).toBeVisible()
  await report.getByLabel('Status').selectOption('RESOLVED')
  await expect(report).toHaveCount(0) // it leaves the Open tab
})

test('an admin locks a user, who can no longer sign in, then unlocks them', async ({ browser, page }) => {
  const [user, admin] = await Promise.all([signUpViaApi('lockme'), signUpViaApi('admin')])
  await makeAdmin(admin.email)

  await signIn(page, admin)
  await page.goto('/admin/users')
  await page.getByLabel('Email or username').fill(user.username)
  await page.getByRole('button', { name: 'Find' }).click()
  await page.getByRole('button', { name: `Lock ${user.username}` }).click()
  await page.getByRole('dialog', { name: `Lock @${user.username}?` }).getByRole('button', { name: 'Lock' }).click()
  await expect(page.getByText(`@${user.username} is locked.`)).toBeVisible()

  const userPage = await (await browser.newContext()).newPage()
  await userPage.goto('/sign-in')
  await userPage.getByLabel('Email').fill(user.email)
  await userPage.getByLabel('Password').fill(user.password)
  await userPage.getByRole('button', { name: 'Sign in' }).click()
  await expect(userPage.getByRole('alert')).toContainText('This account is locked.')

  await page.getByRole('button', { name: `Unlock ${user.username}` }).click()
  await page.getByRole('dialog', { name: `Unlock @${user.username}?` }).getByRole('button', { name: 'Unlock' }).click()
  await expect(page.getByText(`@${user.username} is unlocked.`)).toBeVisible()
  await signIn(userPage, user)
})
