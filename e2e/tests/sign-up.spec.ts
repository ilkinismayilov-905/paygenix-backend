import { expect, test } from '@playwright/test'
import { newIdentity } from '../support/api'
import { codeSentTo } from '../support/mailpit'

test('a new user signs up with the emailed code, signs out and signs back in', async ({ page }) => {
  const user = newIdentity('signup')
  await page.goto('/sign-up')

  await page.getByLabel('Email').fill(user.email)
  const since = new Date()
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Check your email' })).toBeVisible()
  await page.getByLabel('Code').fill(await codeSentTo(user.email, since))
  await page.getByRole('button', { name: 'Continue' }).click()

  await expect(page.getByRole('heading', { name: 'What is your name?' })).toBeVisible()
  await page.getByLabel('Full name').fill(user.fullName)
  await page.getByRole('button', { name: 'Continue' }).click()

  await expect(page.getByRole('heading', { name: 'Pick a username' })).toBeVisible()
  await page.getByLabel('Username').fill(user.username)
  await page.getByRole('button', { name: 'Continue' }).click()

  await expect(page.getByRole('heading', { name: 'Create a password' })).toBeVisible()
  await page.getByLabel('Password').fill(user.password)
  await page.getByRole('button', { name: 'Continue' }).click()

  await expect(page.getByRole('heading', { name: 'Create a PIN' })).toBeVisible()
  await page.getByRole('group', { name: 'PIN' }).locator('input').first().pressSequentially(user.pin)
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Repeat your PIN' })).toBeVisible()
  await page.getByRole('group', { name: 'PIN again' }).locator('input').first().pressSequentially(user.pin)
  await page.getByRole('button', { name: 'Create account' }).click()

  // signed in straight away, on an empty home
  await expect(page.getByRole('heading', { name: /^Hi, E2E/ })).toBeVisible()
  await expect(page.getByText('No cards yet')).toBeVisible()

  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible()
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill('not-the-password')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toContainText('Email or password is incorrect.')
  await page.getByLabel('Password').fill(user.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: /^Hi, E2E/ })).toBeVisible()
})
