import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })
const card = { id: 7, maskedNumber: '**** **** **** 2207', currency: 'AZN', amount: '89.80' }

let setPin: () => Response
const posted: unknown[] = []
beforeEach(() => {
  posted.length = 0
  signedIn('session')
  setPin = () => ok(null)
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/cards/me') return ok([card])
    if (url.pathname === '/api/transfer') return json(403, { code: 'PIN_NOT_SET' })
    if (url.pathname === '/api/user/pin') { posted.push(await req.clone().json()); return setPin() }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = (path = '/pin/setup') => {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}
async function enter(pin: string, button: string) {
  fireEvent.paste(await screen.findByLabelText('Digit 1 of 6'), { clipboardData: { getData: () => pin } })
  fireEvent.click(screen.getByRole('button', { name: button }))
}

test('a transfer without a PIN links to setup, which comes back to the transfer', async () => {
  const router = open('/transfer')
  fireEvent.change(await screen.findByLabelText('To card number'), { target: { value: '4169738812344455' } })
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '5' } })
  await enter('482913', 'Send')
  fireEvent.click(await screen.findByRole('link', { name: 'Set PIN' }))
  expect(await screen.findByRole('heading', { name: 'Create a PIN' })).toBeInTheDocument()
  await enter('135790', 'Continue')
  expect(await screen.findByRole('heading', { name: 'Repeat your PIN' })).toBeInTheDocument()
  await enter('135790', 'Set PIN')
  expect(await screen.findByRole('heading', { name: 'Your PIN is set.' })).toHaveFocus()
  expect(posted).toEqual([{ pin: '135790' }])
  fireEvent.click(screen.getByRole('link', { name: 'Continue' }))
  await vi.waitFor(() => expect(router.state.location.pathname).toBe('/transfer'))
})

test('a different repeat starts over and sends nothing', async () => {
  open()
  await enter('135790', 'Continue')
  await screen.findByRole('heading', { name: 'Repeat your PIN' })
  await enter('135791', 'Set PIN')
  expect(await screen.findByText(/did not match/)).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Create a PIN' })).toBeInTheDocument()
  expect(posted).toHaveLength(0)
})

test('an account that already has a PIN is told so', async () => {
  setPin = () => json(409, { code: 'PIN_ALREADY_SET' })
  open()
  await enter('135790', 'Continue')
  await screen.findByRole('heading', { name: 'Repeat your PIN' })
  await enter('135790', 'Set PIN')
  expect(await screen.findByRole('heading', { name: 'You already have a PIN.' })).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Continue' })).toHaveAttribute('href', '/')
})

test('a failed save keeps the repeat step for a retry', async () => {
  setPin = () => json(500, {})
  open()
  await enter('135790', 'Continue')
  await screen.findByRole('heading', { name: 'Repeat your PIN' })
  await enter('135790', 'Set PIN')
  expect(await screen.findByText(/Could not set the PIN/)).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Repeat your PIN' })).toBeInTheDocument()
})

test('a transfer with a short PIN focuses the PIN', async () => {
  open('/transfer')
  fireEvent.change(await screen.findByLabelText('To card number'), { target: { value: '4169738812344455' } })
  fireEvent.change(screen.getByLabelText('Amount'), { target: { value: '5' } })
  await enter('12', 'Send')
  expect(await screen.findByText('Enter your 6-digit PIN.')).toBeInTheDocument()
  expect(screen.getByLabelText('Digit 1 of 6')).toHaveFocus()
})
