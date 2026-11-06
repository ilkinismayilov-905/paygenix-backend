import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })
const card = { id: 7, maskedNumber: '**** **** **** 2207', currency: 'AZN', amount: '89.80' }
const paid = () => ok({ phoneNumber: '+994501234567', fromCardNumber: '**** **** **** 2207', amount: '12.30', currency: 'AZN' })

type Call = { path: string; key: string | null; body: Record<string, unknown> }
let cardList: unknown[]
let initiate: () => Response | Promise<Response>
let confirm: () => Response | Promise<Response>
const calls: Call[] = []
beforeEach(() => {
  calls.length = 0
  signedIn('session')
  cardList = [card]
  initiate = () => ok('flow-1')
  confirm = paid
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/cards/me') return ok(cardList)
    if (url.pathname.startsWith('/api/v1/payment/')) {
      calls.push({ path: url.pathname, key: req.headers.get('Idempotency-Key'), body: await req.clone().json() })
      return url.pathname.endsWith('initiate') ? initiate() : confirm()
    }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

async function details(phone = '050 123 45 67', amount = '12,3') {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/pay'] })} />)
  fireEvent.change(await screen.findByLabelText('Phone number'), { target: { value: phone } })
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: amount } })
  fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
}
const enterPin = (pin = '482913') => fireEvent.paste(screen.getByLabelText('Digit 1 of 6'), { clipboardData: { getData: () => pin } })
const pay = () => fireEvent.click(screen.getByRole('button', { name: 'Pay' }))
const confirms = () => calls.filter((c) => c.path.endsWith('confirm'))

test('initiate, review, confirm with PIN, result', async () => {
  await details()
  expect(await screen.findByRole('heading', { name: 'Confirm payment' })).toBeInTheDocument()
  expect(screen.getByText('₼12.30')).toBeInTheDocument()
  expect(screen.getByText('+994501234567')).toBeInTheDocument()
  expect(calls[0]!.body).toEqual({ phoneNumber: '+994501234567', fromCardId: 7, amount: '12.30' })
  enterPin()
  pay()
  expect(await screen.findByRole('heading', { name: 'Payment made' })).toHaveFocus()
  expect(confirms()[0]!.body).toEqual({ flowId: 'flow-1', pin: '482913' })
  expect(confirms()[0]!.key).not.toBe(calls[0]!.key)
})

test('confirm keeps one key per flow across a network error and a wrong PIN', async () => {
  let n = 0
  confirm = () => {
    n++
    if (n === 1) throw new TypeError('offline')
    return n === 2 ? json(403, { code: 'INVALID_PIN' }) : paid()
  }
  await details()
  await screen.findByRole('heading', { name: 'Confirm payment' })
  enterPin(); pay()
  expect(await screen.findByText(/did not go through/)).toBeInTheDocument()
  pay()
  expect(await screen.findByText('Wrong PIN. Try again.')).toBeInTheDocument()
  enterPin('111111'); pay()
  await vi.waitFor(() => expect(confirms()).toHaveLength(3))
  await screen.findByRole('heading', { name: 'Payment made' })
  const keys = confirms().map((c) => c.key)
  expect(keys).toHaveLength(3)
  expect(new Set(keys).size).toBe(1)
})

test('initiate resend after a network error reuses its key', async () => {
  let n = 0
  initiate = () => { if (n++ === 0) throw new TypeError('offline'); return ok('flow-1') }
  await details()
  expect(await screen.findByText(/did not go through/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '12.30' } }) // same money
  fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
  await screen.findByRole('heading', { name: 'Confirm payment' })
  expect(calls[1]!.key).toBe(calls[0]!.key)
})

test('a bad phone number never reaches the server', async () => {
  await details('12345')
  expect(await screen.findByText(/Enter an Azerbaijani number/)).toBeInTheDocument()
  expect(calls).toHaveLength(0)
})

test('PIN is required before confirm is sent', async () => {
  await details('+994 50 123 45 67')
  await screen.findByRole('heading', { name: 'Confirm payment' })
  pay()
  expect(await screen.findByText('Enter your 6-digit PIN.')).toBeInTheDocument()
  expect(confirms()).toHaveLength(0)
})

test.each([
  ['PAYMENT_ALREADY_PROCESSED', 409, /already made/],
  ['INSUFFICIENT_BALANCE', 409, /Not enough money/],
  ['RESOURCE_BUSY', 409, /card is busy/],
])('confirm %s shows its message', async (code, status, text) => {
  confirm = () => json(status, { code })
  await details()
  await screen.findByRole('heading', { name: 'Confirm payment' })
  enterPin(); pay()
  expect(await screen.findByText(text)).toBeInTheDocument()
})

test('an expired flow sends the user back to the details', async () => {
  confirm = () => json(404, { code: 'NOT_FOUND' })
  await details()
  await screen.findByRole('heading', { name: 'Confirm payment' })
  enterPin(); pay()
  expect(await screen.findByText(/can no longer be completed/)).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Pay by phone' })).toBeInTheDocument()
})

test('after "card not found" the list reloads and Continue uses a card that still exists', async () => {
  let n = 0
  initiate = () => {
    if (n++ > 0) return ok('flow-2')
    cardList = [{ ...card, id: 9, maskedNumber: '**** **** **** 9999' }] // card 7 was just closed
    return json(404, { code: 'NOT_FOUND' })
  }
  await details()
  expect(await screen.findByText(/no longer available/)).toBeInTheDocument()
  await screen.findByRole('option', { name: /9999/ }) // the reload has landed
  fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
  await screen.findByRole('heading', { name: 'Confirm payment' })
  expect(calls[1]!.body.fromCardId).toBe(9)
})

test('Change details starts a new flow with a new key and an empty PIN', async () => {
  let n = 0
  initiate = () => ok(`flow-${++n}`)
  await details()
  await screen.findByRole('heading', { name: 'Confirm payment' })
  enterPin()
  fireEvent.click(screen.getByRole('button', { name: 'Change details' }))
  fireEvent.click(await screen.findByRole('button', { name: 'Continue' }))
  await screen.findByRole('heading', { name: 'Confirm payment' })
  expect(screen.getByLabelText('Digit 1 of 6')).toHaveValue('')
  expect(calls[1]!.key).not.toBe(calls[0]!.key)
  enterPin(); pay()
  await screen.findByRole('heading', { name: 'Payment made' })
  expect(confirms()[0]!.body.flowId).toBe('flow-2')
})

test('an operator timeout keeps the confirm step and key; a refusal goes back to the form', async () => {
  confirm = () => json(504, { code: 'PROVIDER_TIMEOUT' })
  await details()
  await screen.findByRole('heading', { name: 'Confirm payment' })
  enterPin()
  pay()
  expect(await screen.findByText(/did not answer. Nothing was charged/)).toBeInTheDocument()
  confirm = () => json(422, { code: 'PHONE_TOPUP_REJECTED' })
  enterPin()
  pay()
  expect(await screen.findByText(/did not accept this number/)).toBeInTheDocument()
  expect(screen.getByLabelText('Phone number')).toBeInTheDocument()
  expect(confirms()[1]!.key).toBe(confirms()[0]!.key)
})
