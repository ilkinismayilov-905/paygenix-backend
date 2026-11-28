import { expect, type Page } from '@playwright/test'
import type { TestUser } from './api'

export async function signIn(page: Page, user: TestUser): Promise<void> {
  await page.goto('/sign-in')
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill(user.password)
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('heading', { name: /^Hi, / })).toBeVisible()
}

/** Types a 6-digit PIN into the PIN boxes; the first box moves focus along as digits arrive. */
export async function enterPin(page: Page, pin: string, label = 'Digit 1 of 6'): Promise<void> {
  await page.getByLabel(label).first().pressSequentially(pin)
}
