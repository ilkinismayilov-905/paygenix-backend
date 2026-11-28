import { expect, test } from '@playwright/test'
import { cardsOf, issueCard, signUpViaApi } from '../support/api'
import { fundCard } from '../support/db'
import { codeSentTo } from '../support/mailpit'
import { enterPin, signIn } from '../support/ui'

test('change the PIN with an emailed code; only the new PIN confirms a payment', async ({ page }) => {
  const user = await signUpViaApi('pin')
  await fundCard(await issueCard(user), user.email, '10.00')
  const newPin = '135792'

  await signIn(page, user)
  await page.goto('/settings/pin')
  const since = new Date()
  await page.getByRole('button', { name: 'Email me a code' }).click()
  await page.getByLabel('Code').fill(await codeSentTo(user.email, since))
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Choose a new PIN' })).toBeVisible()
  await enterPin(page, newPin)
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Repeat your PIN' })).toBeVisible()
  await enterPin(page, newPin)
  await page.getByRole('button', { name: 'Change PIN' }).click()
  await expect(page.getByRole('heading', { name: 'Your PIN is changed.' })).toBeVisible()

  await page.goto('/pay')
  await page.getByLabel('Phone number').fill('050 765 43 21')
  await page.getByLabel('Amount').fill('1')
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Confirm payment' })).toBeVisible()
  await enterPin(page, user.pin) // the old one
  await page.getByRole('button', { name: 'Pay' }).click()
  await expect(page.getByText('Wrong PIN. Try again.')).toBeVisible()
  await enterPin(page, newPin)
  await page.getByRole('button', { name: 'Pay' }).click()
  await expect(page.getByRole('heading', { name: 'Payment made' })).toBeVisible()
  // 10.00 - 1.00 + 0.5% cashback (0.005, half-even to 0.00)
  expect((await cardsOf(user))[0].amount).toBe('9.00')
})
