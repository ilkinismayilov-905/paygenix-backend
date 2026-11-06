import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { getAccessToken, signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
const ok = (data: unknown) => json(200, { data })

let start: () => Response
let confirm: () => Response
const calls: { path: string; body: unknown }[] = []
beforeEach(() => {
  calls.length = 0
  signedIn('session')
  start = () => ok(null)
  confirm = () => ok(null)
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname.startsWith('/api/v1/profile/email')) {
      calls.push({ path: url.pathname, body: await req.clone().json() })
      return url.pathname.endsWith('/confirm') ? confirm() : start()
    }
    if (url.pathname === '/api/v1/auth/logout') return ok(null)
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/settings/email'] })} />)
const click = (name: string) => fireEvent.click(screen.getByRole('button', { name }))
async function fillStart(email = ' New@Example.test ', pin = '123456') {
  open()
  fireEvent.change(await screen.findByLabelText('New email'), { target: { value: email } })
  fireEvent.paste(screen.getByLabelText('Digit 1 of 6'), { clipboardData: { getData: () => pin } })
  click('Send codes')
}
async function fillCodes() {
  fireEvent.change(await screen.findByLabelText('Code sent to new@example.test'), { target: { value: '111 111' } })
  fireEvent.change(screen.getByLabelText('Code sent to your current email'), { target: { value: '222222' } })
  click('Change email')
}

test('new email + PIN, then both codes change the email and sign this tab out', async () => {
  await fillStart()
  await fillCodes()
  expect(await screen.findByRole('heading', { name: 'Your email is changed.' })).toHaveFocus()
  expect(screen.getByText(/Sign in again with new@example.test/)).toBeInTheDocument()
  expect(calls).toEqual([
    { path: '/api/v1/profile/email', body: { email: 'new@example.test', pin: '123456' } },
    { path: '/api/v1/profile/email/confirm', body: { otp: '111111', currentEmailOtp: '222222' } },
  ])
  click('Sign in')
  expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  expect(getAccessToken()).toBeNull()
})

test('a wrong PIN clears it and stays on the form', async () => {
  start = () => json(403, { code: 'INVALID_PIN' })
  await fillStart()
  expect(await screen.findByText('Wrong PIN. Try again.')).toBeInTheDocument()
  expect(screen.getByLabelText('New email')).toHaveValue('New@Example.test')
})

test('a taken email is shown on the email field', async () => {
  start = () => json(409, { code: 'EMAIL_TAKEN' })
  await fillStart()
  expect(await screen.findByText('This email is already used by another account.')).toBeInTheDocument()
})

test('no PIN yet links to PIN setup', async () => {
  start = () => json(403, { code: 'PIN_NOT_SET' })
  await fillStart()
  expect(await screen.findByRole('link', { name: 'Set PIN' })).toHaveAttribute('href', '/pin/setup')
})

test.each([
  ['OTP_SEND_TOO_FREQUENT', '60', 'A code was sent a moment ago. Try again in 1 min.'],
  ['EMAIL_CHANGE_LIMIT', '7200', 'Too many email changes today. Try again in 2 h.'],
  ['PIN_LOCKED', '900', 'Too many wrong PINs. Try again in 15 min.'],
])('429 %s shows the wait', async (code, retryAfter, text) => {
  start = () => json(429, { code }, { 'Retry-After': retryAfter })
  await fillStart()
  expect(await screen.findByText(text)).toBeInTheDocument()
})

test('a wrong code clears both and stays on the codes step', async () => {
  confirm = () => json(400, { code: 'INVALID_OTP' })
  await fillStart()
  await fillCodes()
  expect(await screen.findByText(/One of the codes is not right/)).toBeInTheDocument()
  expect(screen.getByLabelText('Code sent to new@example.test')).toHaveValue('')
  expect(screen.getByLabelText('Code sent to your current email')).toHaveValue('')
})

test.each([
  [400, 'OTP_EXPIRED', /has expired/],
  [429, 'TOO_MANY_ATTEMPTS', /Too many wrong codes/],
  [409, 'EMAIL_TAKEN', /just taken/],
])('%i %s goes back to the start with the email kept', async (status, code, text) => {
  confirm = () => json(status, { code })
  await fillStart()
  await fillCodes()
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Change your email' })).toBeInTheDocument()
  expect(screen.getByLabelText('New email')).toHaveValue('new@example.test')
  expect(screen.getByLabelText('New email')).toHaveFocus()
})

test('codes must be 6 digits before anything is sent', async () => {
  await fillStart()
  fireEvent.change(await screen.findByLabelText('Code sent to new@example.test'), { target: { value: '12' } })
  click('Change email')
  expect(await screen.findAllByText('Enter the 6 digits from the email.')).toHaveLength(2)
  expect(calls).toHaveLength(1)
})
