import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
const ok = (data: unknown) => json(200, { data })

let start: () => Response
let otp: () => Response
let pin: () => Response
const calls: { path: string; body: unknown }[] = []
beforeEach(() => {
  calls.length = 0
  signedIn('session')
  start = () => ok('flow-1')
  otp = () => ok(null)
  pin = () => ok(null)
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname.startsWith('/api/change-pin')) {
      calls.push({ path: url.pathname, body: req.body ? await req.clone().json() : null })
      if (url.pathname === '/api/change-pin') return start()
      return url.pathname.endsWith('/otp') ? otp() : pin()
    }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/settings/pin'] })} />)
const click = (name: string) => fireEvent.click(screen.getByRole('button', { name }))
async function toCode() {
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Email me a code' }))
  fireEvent.change(await screen.findByLabelText('Code'), { target: { value: '123 456' } })
  click('Continue')
}
async function enterPin(value: string, button: string) {
  fireEvent.paste(await screen.findByLabelText('Digit 1 of 6'), { clipboardData: { getData: () => value } })
  click(button)
}

test('code, new PIN and repeat change the PIN', async () => {
  await toCode()
  expect(await screen.findByRole('heading', { name: 'Choose a new PIN' })).toBeInTheDocument()
  await enterPin('246810', 'Continue')
  await screen.findByRole('heading', { name: 'Repeat your PIN' })
  await enterPin('246810', 'Change PIN')
  expect(await screen.findByRole('heading', { name: 'Your PIN is changed.' })).toHaveFocus()
  expect(calls).toEqual([
    { path: '/api/change-pin', body: null },
    { path: '/api/change-pin/flow-1/otp', body: { otp: '123456' } },
    { path: '/api/change-pin/flow-1/pin', body: { newPin: '246810', reEnterNewPin: '246810' } },
  ])
})

test('a wrong code stays on the code step', async () => {
  otp = () => json(400, { code: 'INVALID_OTP' })
  await toCode()
  expect(await screen.findByText(/code is not right/)).toBeInTheDocument()
  expect(screen.getByLabelText('Code')).toBeInTheDocument()
})

test.each([
  [410, 'FLOW_EXPIRED', /has expired/],
  [404, 'NOT_FOUND', /has expired/],
  [409, 'INVALID_STEP', /has expired/],
  [429, 'TOO_MANY_ATTEMPTS', /Request a new code/],
])('%i %s goes back to the start', async (status, code, text) => {
  otp = () => json(status, { code })
  await toCode()
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Change your PIN' })).toHaveFocus()
  expect(screen.getByRole('button', { name: 'Email me a code' })).toBeInTheDocument()
})

test('a new code starts a new flow and says so; offline keeps the step', async () => {
  let n = 0
  start = () => ok(`flow-${++n}`)
  otp = () => { throw new TypeError('offline') }
  await toCode()
  expect(await screen.findByText(/Check your connection/)).toBeInTheDocument()
  click('Send a new code')
  expect(await screen.findByText('A new code is on its way.')).toBeInTheDocument()
  expect(screen.getByLabelText('Code')).toHaveValue('')
  otp = () => ok(null)
  fireEvent.change(screen.getByLabelText('Code'), { target: { value: '654321' } })
  click('Continue')
  await screen.findByRole('heading', { name: 'Choose a new PIN' })
  expect(calls.at(-1)).toEqual({ path: '/api/change-pin/flow-2/otp', body: { otp: '654321' } })
})

test('a code asked for too soon shows the wait', async () => {
  start = () => json(429, { code: 'OTP_SEND_TOO_FREQUENT' }, { 'Retry-After': '60' })
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Email me a code' }))
  expect(await screen.findByText('A code was sent a moment ago. Try again in 1 min.')).toBeInTheDocument()
})

test('the same PIN as before asks for another; a mismatch sends nothing', async () => {
  pin = () => json(400, { code: 'PIN_UNCHANGED' })
  await toCode()
  await enterPin('246810', 'Continue')
  await screen.findByRole('heading', { name: 'Repeat your PIN' })
  await enterPin('246811', 'Change PIN')
  expect(await screen.findByText(/did not match/)).toBeInTheDocument()
  expect(calls).toHaveLength(2)
  await enterPin('246810', 'Continue')
  await screen.findByRole('heading', { name: 'Repeat your PIN' })
  await enterPin('246810', 'Change PIN')
  expect(await screen.findByText('The new PIN must differ from the current one.')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Choose a new PIN' })).toBeInTheDocument()
})
