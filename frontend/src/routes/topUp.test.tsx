import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
const ok = (data: unknown) => json(200, { data })
const cards = [{ id: 7, maskedNumber: '**** 2207', currency: 'AZN', amount: '1.00' }, { id: 8, maskedNumber: '**** 3308', currency: 'USD', amount: '5.00' }]

let start: () => Response
let byId: () => Response
const posted: { body: unknown; key: string | null }[] = []
const assign = vi.fn()
beforeEach(() => {
  posted.length = 0
  assign.mockReset()
  signedIn('session')
  start = () => json(201, { data: { id: 1, status: 'PENDING', checkoutUrl: 'https://checkout.stripe.test/cs_1' } })
  byId = () => ok({ id: 1, status: 'SUCCEEDED', amount: '25.50', currency: 'AZN' })
  vi.stubGlobal('location', { ...window.location, origin: window.location.origin, assign })
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/cards/me') return ok(cards)
    if (url.pathname === '/api/v1/topups') { posted.push({ body: await req.clone().json(), key: req.headers.get('Idempotency-Key') }); return start() }
    if (url.pathname === '/api/v1/topups/session/cs_1') return byId()
    return json(404, {})
  })
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); signedOut() })

const open = (path: string) => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />)

test('opens the checkout for the chosen card and amount', async () => {
  open('/top-up?card=8')
  fireEvent.change(await screen.findByLabelText('Amount'), { target: { value: '25,5' } })
  expect(screen.getByLabelText('Card')).toHaveValue('8')
  fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }))
  await vi.waitFor(() => expect(assign).toHaveBeenCalledWith('https://checkout.stripe.test/cs_1'))
  expect(posted).toEqual([{ body: { cardId: 8, amount: '25.50' }, key: expect.any(String) }])
})

test('amounts outside 1-10000 are refused; switched-off top-ups say so; a retry keeps the key', async () => {
  start = () => json(503, { code: 'TOP_UP_UNAVAILABLE' }, { 'Retry-After': '30' })
  open('/top-up')
  fireEvent.change(await screen.findByLabelText('Amount'), { target: { value: '0.5' } })
  fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }))
  expect(await screen.findByText('Enter an amount from 1.00 to 10000.00.')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '10' } })
  fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }))
  expect(await screen.findByText('Top-ups are not available right now. Try again in 1 min.')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }))
  await vi.waitFor(() => expect(posted).toHaveLength(2))
  expect(posted[1]!.key).toBe(posted[0]!.key)
})

test('the return page waits for the webhook, then shows the money added', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  let calls = 0
  byId = () => ok(++calls < 2 ? { id: 1, status: 'PENDING', amount: '25.50', currency: 'AZN' } : { id: 1, status: 'SUCCEEDED', amount: '25.50', currency: 'AZN' })
  open('/top-up/return?session=cs_1')
  expect(await screen.findByText(/Waiting for Stripe/)).toBeInTheDocument()
  await vi.advanceTimersByTimeAsync(2100)
  expect(await screen.findByRole('heading', { name: 'Money added' })).toHaveFocus()
  expect(screen.getByText('₼25.50')).toBeInTheDocument()
})

test('a cancelled checkout says nothing was charged', async () => {
  byId = () => ok({ id: 1, status: 'PENDING', amount: '25.50', currency: 'AZN' })
  open('/top-up/return?cancelled=1')
  expect(await screen.findByText('The payment was cancelled. Nothing was charged.')).toBeInTheDocument()
})

test('an unknown session says so', async () => {
  open('/top-up/return?session=cs_other')
  expect(await screen.findByText('This top-up could not be found.')).toBeInTheDocument()
})

test('a finished top-up replay starts over with a new key', async () => {
  start = () => json(409, { code: 'TOP_UP_FINISHED' })
  open('/top-up')
  fireEvent.change(await screen.findByLabelText('Amount'), { target: { value: '10' } })
  fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }))
  expect(await screen.findByText(/already finished/)).toBeInTheDocument()
  start = () => json(201, { data: { id: 2, status: 'PENDING', checkoutUrl: 'https://checkout.stripe.test/cs_2' } })
  fireEvent.click(screen.getByRole('button', { name: 'Continue to payment' }))
  await vi.waitFor(() => expect(assign).toHaveBeenCalled())
  expect(posted[1]!.key).not.toBe(posted[0]!.key)
})
