import { expect, test } from '@playwright/test'
import { signUpViaApi } from '../support/api'
import { makeAdmin } from '../support/db'
import { signIn } from '../support/ui'

// A real 2x2 PNG: the API checks the declared type against the file's first bytes.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAFklEQVR4nGP8z8DAwMDAxMDAwMDAAAANHQEDasKb6QAAAABJRU5ErkJggg==',
  'base64',
)

test('a user sends an identity document to the private bucket and an admin approves it', async ({ browser }) => {
  const [user, admin] = await Promise.all([signUpViaApi('kyc'), signUpViaApi('admin')])
  await makeAdmin(admin.email)

  const userPage = await (await browser.newContext()).newPage()
  await signIn(userPage, user)
  await userPage.goto('/settings/verify')
  await userPage.getByLabel('Passport').check()
  await userPage.getByLabel('Photo of the document').setInputFiles({ name: 'passport.png', mimeType: 'image/png', buffer: PNG })
  await userPage.getByRole('button', { name: 'Send for review' }).click()
  await expect(userPage.getByText('We are checking your document.')).toBeVisible()

  const adminPage = await (await browser.newContext()).newPage()
  await signIn(adminPage, admin)
  await adminPage.goto('/admin/kyc')
  // pending reviews of earlier runs may come first: page on until this user's shows up
  const link = adminPage.getByRole('link', { name: user.fullName })
  const more = adminPage.getByRole('button', { name: 'Show more' })
  await expect(link.or(more).first()).toBeVisible()
  for (let round = 0; !(await link.isVisible()); round++) {
    expect(round, 'pages of pending reviews searched').toBeLessThan(20)
    await more.click()
    await expect(link.or(more).first()).toBeVisible()
  }
  await link.click()
  // the document is shown from a short-lived presigned link to the private bucket
  const image = adminPage.locator('img.doc-photo')
  await expect(image).toBeVisible()
  await expect.poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth)).toBe(2)
  await adminPage.getByRole('button', { name: 'Approve' }).click()
  await adminPage.getByRole('dialog', { name: 'Approve this document?' }).getByRole('button', { name: 'Approve' }).click()
  await expect(adminPage.getByText(`${user.fullName} is verified.`)).toBeVisible()

  await userPage.reload()
  await expect(userPage.getByText('We are checking your document.')).toHaveCount(0)
})
