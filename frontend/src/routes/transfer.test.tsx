import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })
const card = { id: 7, maskedNumber: '**** **** **** 2207', currency: 'AZN', amount: '89.80' }

let transfer: (req: Request) => Promise<Response> | Response
const sent: { key: string | null; body: unknown }[] = []
beforeEach(() => {
  sent.length = 0
  signedIn('session')
  transfer = () => ok({ fromCardNumber: '**** **** **** 2207', toCardNumber: '**** **** **** 4455', amount: '10.50', currency: 'AZN' })
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/cards/me') return ok([card])
    if (url.pathname === '/api/v1/fees') return ok({ commissionPercent: '2', cashbackPercent: '0.5' })
    if (url.pathname === '/api/v1/transfer') {
      sent.push({ key: req.headers.get('Idempotency-Key'), body: await req.clone().json() })
      return transfer(req)
    }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

async function fill(pin = '482913') {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/transfer'] })} />)
  fireEvent.change(await screen.findByLabelText('To card number'), { target: { value: '4169 7388 1234 4455' } })
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '10,50' } })
  fireEvent.paste(screen.getByLabelText('Digit 1 of 6'), { clipboardData: { getData: () => pin } })
}
const send = () => fireEvent.click(screen.getByRole('button', { name: 'Send' }))

test('sends a normalized body with an Idempotency-Key and shows the result', async () => {
  await fill()
  send()
  expect(await screen.findByRole('heading', { name: 'Money sent' })).toBeInTheDocument()
  expect(screen.getByText('₼10.50')).toBeInTheDocument()
  expect(screen.getByText('To the card ending in 4455')).toBeInTheDocument()
  expect(sent).toHaveLength(1)
  expect(sent[0]!.body).toEqual({ fromCardId: 7, toCardNumber: '4169738812344455', amount: '10.50', pin: '482913' })
  expect(sent[0]!.key).toMatch(/^[0-9a-f-]{36}$/)
})

test('a resend after a network error reuses the key; a changed body gets a new one', async () => {
  let calls = 0
  transfer = () => { if (calls++ === 0) throw new TypeError('offline'); return json(409, { code: 'INSUFFICIENT_BALANCE' }) }
  await fill()
  send()
  expect(await screen.findByText(/did not go through/)).toBeInTheDocument()
  send()
  expect(await screen.findByText(/Not enough money/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '010.5' } }) // same money, other spelling
  fireEvent.paste(screen.getByLabelText('Digit 1 of 6'), { clipboardData: { getData: () => '111111' } })
  send()
  await vi.waitFor(() => expect(sent).toHaveLength(3))
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '5' } })
  send()
  await vi.waitFor(() => expect(sent).toHaveLength(4))
  expect(sent[1]!.key).toBe(sent[0]!.key)
  expect(sent[2]!.key).toBe(sent[0]!.key)
  expect(sent[3]!.key).not.toBe(sent[0]!.key)
})

test('a wrong PIN clears the PIN and says so', async () => {
  transfer = () => json(403, { code: 'INVALID_PIN' })
  await fill()
  send()
  expect(await screen.findByText('Wrong PIN. Try again.')).toBeInTheDocument()
  expect(screen.getByLabelText('Digit 1 of 6')).toHaveValue('')
})

test('PIN lock shows the wait from Retry-After', async () => {
  transfer = () => new Response(JSON.stringify({ code: 'PIN_LOCKED' }), { status: 429, headers: { 'Content-Type': 'application/json', 'Retry-After': '600' } })
  await fill()
  send()
  expect(await screen.findByText('Too many wrong PINs. Try again in 10 min.')).toBeInTheDocument()
})

test('invalid input never reaches the server', async () => {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/transfer'] })} />)
  fireEvent.change(await screen.findByLabelText('To card number'), { target: { value: '1234' } })
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '0.00' } })
  send()
  expect(await screen.findByText('Enter the 16 digits of the card number.')).toBeInTheDocument()
  expect(screen.getByText('Enter an amount like 10 or 10.50.')).toBeInTheDocument()
  expect(screen.getByText('Enter your 6-digit PIN.')).toBeInTheDocument()
  expect(sent).toHaveLength(0)
})

test('a second press while sending does not send twice; the result gets focus', async () => {
  let answer!: (r: Response) => void
  transfer = () => new Promise((r) => { answer = r })
  await fill()
  send()
  send()
  await vi.waitFor(() => expect(sent).toHaveLength(1))
  answer(ok({ toCardNumber: '**** **** **** 4455', amount: '10.50', currency: 'AZN' }))
  expect(await screen.findByRole('heading', { name: 'Money sent' })).toHaveFocus()
  expect(sent).toHaveLength(1)
})

test.each([
  ['NOT_FOUND', 404, /Card not found/],
  ['SAME_CARD', 400, /different card/],
  ['CURRENCY_MISMATCH', 400, /same currency/],
  ['RESOURCE_BUSY', 409, /card is busy/],
  ['SOMETHING_NEW', 500, /could not be sent/],
])('%s shows its message', async (code, status, text) => {
  transfer = () => json(status, { code })
  await fill()
  send()
  expect(await screen.findByText(text)).toBeInTheDocument()
})

test('shows the fee and the total for the typed amount', async () => {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/transfer'] })} />)
  expect(await screen.findByLabelText('From card')).toHaveValue('7') // the card used is the card shown, never a blank select
  expect(await screen.findByText('A 2% fee is added to this amount.')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '10,5' } })
  expect(await screen.findByText('Fee 2%: ₼0.21. Total: ₼10.71.')).toBeInTheDocument()
})
