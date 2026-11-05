import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { getAccessToken, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
const ok = (data: unknown = null) => json(200, { data })

let fetchMock: ReturnType<typeof vi.fn>
beforeEach(() => { signedOut(); fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock) })
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const type = (label: string, value: string) => fireEvent.input(screen.getByLabelText(label), { target: { value } })
const next = () => fireEvent.click(screen.getByRole('button', { name: 'Continue' }))
const pin = (legend: string, digits: string) =>
  fireEvent.paste(screen.getByRole('group', { name: legend }).querySelector('input')!, { clipboardData: { getData: () => digits } })

async function reachUsername() {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/sign-up'] })} />)
  fireEvent.input(await screen.findByLabelText('Email'), { target: { value: 'nigar@example.test' } })
  next()
  await screen.findByRole('heading', { name: 'Check your email' })
  type('Code', '123456')
  next()
  await screen.findByRole('heading', { name: 'What is your name?' })
  type('Full name', 'Nigar Aliyeva')
  next()
  await screen.findByRole('heading', { name: 'Pick a username' })
  expect(getAccessToken()).toBeNull() // the onboarding token never becomes the session
}

async function reachSecure() {
  await reachUsername()
  type('Username', 'nigar')
  next()
  await screen.findByRole('heading', { name: 'Secure your account' })
}

test('the whole sign-up sends each step in order with the onboarding token, then signs in', async () => {
  const calls: { url: string; auth: string | null; body: unknown }[] = []
  fetchMock.mockImplementation(async (req: Request) => {
    const url = new URL(req.url).pathname
    calls.push({ url, auth: req.headers.get('Authorization'), body: await req.json() })
    if (url === '/api/onboarding/email') return ok('flow-1')
    if (url === '/api/onboarding/flow-1/otp') return ok('onb-token')
    if (url === '/api/auth/login') return ok({ token: 'session' })
    return ok()
  })
  await reachUsername()
  expect(screen.getByLabelText('Username')).toHaveValue('') // not carried over from the name step
  type('Username', 'nigar.a')
  next()
  await screen.findByRole('heading', { name: 'Secure your account' })
  type('Password', 'long-enough-pass')
  type('Repeat password', 'long-enough-pass')
  pin('PIN', '482913')
  pin('Repeat PIN', '482913')
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByRole('heading', { name: 'Welcome to Paygenix' })).toBeInTheDocument()

  expect(calls.map((c) => c.url)).toEqual([
    '/api/onboarding/email', '/api/onboarding/flow-1/otp', '/api/onboarding/flow-1/full-name',
    '/api/onboarding/flow-1/username', '/api/onboarding/flow-1/pin', '/api/auth/login',
  ])
  expect(calls.slice(2, 5).every((c) => c.auth === 'Bearer onb-token')).toBe(true)
  expect(calls[4]!.body).toEqual({ pin: '482913', password: 'long-enough-pass' })
  expect(getAccessToken()).toBe('session')
})

test('a taken username stays on the step with a message', async () => {
  fetchMock.mockImplementation(async (req: Request) => {
    const url = new URL(req.url).pathname
    if (url.endsWith('/username')) return json(409, { code: 'USERNAME_TAKEN' })
    return ok(url.endsWith('/email') ? 'flow-1' : 'tok')
  })
  await reachUsername()
  type('Username', 'taken')
  next()
  expect(await screen.findByRole('alert')).toHaveTextContent('That username is taken')
  expect(screen.getByRole('heading', { name: 'Pick a username' })).toBeInTheDocument()
})

test('an expired flow offers to start again', async () => {
  fetchMock.mockImplementation(async (req: Request) =>
    new URL(req.url).pathname.endsWith('/otp') ? json(410, { code: 'FLOW_EXPIRED' }) : ok('flow-1'))
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/sign-up'] })} />)
  fireEvent.input(await screen.findByLabelText('Email'), { target: { value: 'nigar@example.test' } })
  next()
  await screen.findByRole('heading', { name: 'Check your email' })
  type('Code', '123456')
  next()
  expect(await screen.findByRole('alert')).toHaveTextContent('This sign-up has expired')
  fireEvent.click(screen.getByRole('button', { name: 'Start again' }))
  expect(await screen.findByRole('heading', { name: 'Create your account' })).toBeInTheDocument()
})

test('mismatched password and PIN are caught before sending', async () => {
  fetchMock.mockImplementation(async (req: Request) => ok(new URL(req.url).pathname.endsWith('/email') ? 'flow-1' : 'tok'))
  await reachSecure()
  const before = fetchMock.mock.calls.length
  type('Password', 'long-enough-pass')
  type('Repeat password', 'different-pass')
  pin('PIN', '111111')
  pin('Repeat PIN', '222222')
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByText('The passwords do not match.')).toBeInTheDocument()
  expect(screen.getByText('The PINs do not match.')).toBeInTheDocument()
  expect(fetchMock.mock.calls.length).toBe(before)
})

test('if sign-in fails after the account is created, the sign-in page takes over', async () => {
  fetchMock.mockImplementation(async (req: Request) => {
    const url = new URL(req.url).pathname
    if (url === '/api/auth/login') return Promise.reject(new TypeError('offline'))
    return ok(url.endsWith('/email') ? 'flow-1' : 'tok')
  })
  await reachSecure()
  type('Password', 'long-enough-pass')
  type('Repeat password', 'long-enough-pass')
  pin('PIN', '482913')
  pin('Repeat PIN', '482913')
  fireEvent.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent('Your account is ready')
})

test('a new code replaces the flow and says so; asking too soon is explained', async () => {
  let flows = 0
  fetchMock.mockImplementation(async (req: Request) => {
    const url = new URL(req.url).pathname
    if (url === '/api/onboarding/email') return ++flows === 3 ? json(429, { code: 'OTP_SEND_TOO_FREQUENT' }) : ok(`flow-${flows}`)
    return ok('tok')
  })
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/sign-up'] })} />)
  fireEvent.input(await screen.findByLabelText('Email'), { target: { value: 'nigar@example.test' } })
  next()
  await screen.findByRole('heading', { name: 'Check your email' })
  fireEvent.click(screen.getByRole('button', { name: 'Send a new code' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Earlier codes no longer work')
  fireEvent.click(screen.getByRole('button', { name: 'Send a new code' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Wait a minute')
  type('Code', ' 123 456 ')
  next()
  await screen.findByRole('heading', { name: 'What is your name?' })
  expect(fetchMock.mock.calls.map(([r]) => new URL((r as Request).url).pathname)).toContain('/api/onboarding/flow-2/otp')
})

test('the username check limit shows the Retry-After minutes', async () => {
  fetchMock.mockImplementation(async (req: Request) => {
    const url = new URL(req.url).pathname
    if (url.endsWith('/username')) return json(429, { code: 'USERNAME_CHECK_LIMIT' }, { 'Retry-After': '420' })
    return ok(url.endsWith('/email') ? 'flow-1' : 'tok')
  })
  await reachUsername()
  type('Username', 'nigar')
  next()
  expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 7 min.')
})
