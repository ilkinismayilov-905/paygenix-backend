import { expect, test } from '@playwright/test'
import { cardsOf, issueCard, signUpViaApi } from '../support/api'
import { fundCard } from '../support/db'
import { enterPin, signIn } from '../support/ui'

test('transfer to another user: the fee is shown before sending and both balances move', async ({ page }) => {
  const [sender, receiver] = await Promise.all([signUpViaApi('sender'), signUpViaApi('receiver')])
  const [from, to] = await Promise.all([issueCard(sender), issueCard(receiver)])
  await fundCard(from, sender.email, '100.00')

  await signIn(page, sender)
  await page.goto('/transfer')
  await page.getByLabel('To card number').fill(to)
  await page.getByLabel('Amount').fill('10,50')
  await expect(page.getByText('Fee 2%: ₼0.21. Total: ₼10.71.')).toBeVisible()
  await enterPin(page, sender.pin)
  await page.getByRole('button', { name: 'Send' }).click()

  await expect(page.getByRole('heading', { name: 'Money sent' })).toBeVisible()
  await expect(page.getByText(`To the card ending in ${to.slice(-4)}`)).toBeVisible()
  expect((await cardsOf(sender))[0].amount).toBe('89.29')
  expect((await cardsOf(receiver))[0].amount).toBe('10.50')
})

test('a wrong PIN is refused and moves no money', async ({ page }) => {
  const [sender, receiver] = await Promise.all([signUpViaApi('sender'), signUpViaApi('receiver')])
  const [from, to] = await Promise.all([issueCard(sender), issueCard(receiver)])
  await fundCard(from, sender.email, '20.00')

  await signIn(page, sender)
  await page.goto('/transfer')
  await page.getByLabel('To card number').fill(to)
  await page.getByLabel('Amount').fill('5')
  await enterPin(page, '000000')
  await page.getByRole('button', { name: 'Send' }).click()

  await expect(page.getByText('Wrong PIN. Try again.')).toBeVisible()
  expect((await cardsOf(sender))[0].amount).toBe('20.00')
  expect((await cardsOf(receiver))[0].amount).toBe('0.00')
})

test('phone payment through the mock operator, and a number the operator refuses', async ({ page }) => {
  const user = await signUpViaApi('payer')
  await fundCard(await issueCard(user), user.email, '50.00')

  await signIn(page, user)
  await page.goto('/pay')
  await page.getByLabel('Phone number').fill('050 123 45 67')
  await page.getByLabel('Amount').fill('12,3')
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Confirm payment' })).toBeVisible()
  await expect(page.getByText('+994501234567')).toBeVisible()
  await enterPin(page, user.pin)
  await page.getByRole('button', { name: 'Pay' }).click()
  await expect(page.getByRole('heading', { name: 'Payment made' })).toBeVisible()
  // 50.00 - 12.30 + 0.5% cashback (0.0615, half-even to 0.06)
  expect((await cardsOf(user))[0].amount).toBe('37.76')

  // the mock operator refuses numbers ending in 000; nothing is charged
  await page.goto('/pay')
  await page.getByLabel('Phone number').fill('050 123 40 00')
  await page.getByLabel('Amount').fill('5')
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Confirm payment' })).toBeVisible()
  await enterPin(page, user.pin)
  await page.getByRole('button', { name: 'Pay' }).click()
  await expect(page.getByText('The mobile operator did not accept this number. Nothing was charged.')).toBeVisible()
  expect((await cardsOf(user))[0].amount).toBe('37.76')
})

test('an operator answer that is lost: the amount is held, and Pay again completes the payment once', async ({ page }) => {
  const user = await signUpViaApi('held')
  await fundCard(await issueCard(user), user.email, '30.00')

  await signIn(page, user)
  await page.goto('/pay')
  // the mock operator delivers to ...998 but its answer is lost
  await page.getByLabel('Phone number').fill('050 123 49 98')
  await page.getByLabel('Amount').fill('10')
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByRole('heading', { name: 'Confirm payment' })).toBeVisible()
  await enterPin(page, user.pin)
  await page.getByRole('button', { name: 'Pay' }).click()
  await expect(page.getByText('The mobile operator did not answer yet. The amount is on hold, not paid.')).toBeVisible()
  expect((await cardsOf(user))[0].amount).toBe('20.00')

  // the PIN stays entered: "Press Pay again" is all it takes
  await page.getByRole('button', { name: 'Pay' }).click()
  await expect(page.getByRole('heading', { name: 'Payment made' })).toBeVisible()
  expect((await cardsOf(user))[0].amount).toBe('20.05') // charged once, plus 0.5% cashback
})
