import { fireEvent, render, screen } from '@testing-library/react'
import { StrictMode } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { api } from '../api/client'
import { routes } from '../router'
import { safeReturnPath } from './returnPath'
import { getAccessToken, refresh, signedIn, signedOut } from './session'
import { signOut } from './signOut'

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })

let fetchMock: ReturnType<typeof vi.fn>
beforeEach(() => { fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock) })
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const urlOf = (input: RequestInfo | URL) => (input instanceof Request ? input.url : String(input))

test('parallel 401s share one refresh and each retries once with the new token', async () => {
  signedIn('old')
  let refreshes = 0
  fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
    if (urlOf(input).endsWith('/api/auth/refresh-token')) { refreshes++; return json(200, { data: { token: 'new' } }) }
    const auth = (input as Request).headers.get('Authorization')
    return auth === 'Bearer new' ? json(200, { data: [] }) : json(401, { code: 'UNAUTHORIZED' })
  })

  const [a, b] = await Promise.all([api.GET('/api/auth/sessions'), api.GET('/api/cards/me')])
  expect(a.response.status).toBe(200)
  expect(b.response.status).toBe(200)
  expect(refreshes).toBe(1)
  expect(getAccessToken()).toBe('new')
})

test('a failed refresh signs out and the 401 is returned', async () => {
  signedIn('old')
  fetchMock.mockImplementation(async (input: RequestInfo | URL) =>
    urlOf(input).endsWith('/api/auth/refresh-token') ? json(401, { code: 'REFRESH_TOKEN_REUSED' }) : json(401, {}))
  const { response } = await api.GET('/api/auth/sessions')
  expect(response.status).toBe(401)
  expect(getAccessToken()).toBeNull()
})

test('a 401 without a token (wrong password) never triggers a refresh', async () => {
  fetchMock.mockResolvedValue(json(401, { code: 'UNAUTHORIZED' }))
  await api.POST('/api/auth/login', { body: { email: 'a@b.c', password: 'x' } })
  expect(fetchMock).toHaveBeenCalledTimes(1)
})

test('startup without a refresh cookie shows sign-in for a protected page', async () => {
  fetchMock.mockResolvedValue(json(401, { code: 'INVALID_REFRESH_TOKEN' }))
  await refresh()
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/'] })} />)
  expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
})

test.each([
  [401, 'UNAUTHORIZED', {}, 'Email or password is incorrect.'],
  [403, 'ACCOUNT_LOCKED', {}, 'This account is locked. Contact support to unlock it.'],
  [429, 'LOGIN_LOCKED', { 'Retry-After': '540' }, 'Too many attempts. Try again in 9 min.'],
])('sign-in maps %s %s to a message', async (status, code, headers, message) => {
  signedOut()
  fetchMock.mockResolvedValue(json(status, { code }, headers))
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/sign-in'] })} />)
  fireEvent.input(await screen.findByLabelText('Email'), { target: { value: 'aysel@example.test' } })
  fireEvent.input(screen.getByLabelText('Password'), { target: { value: 'secret-pass' } })
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('alert')).toHaveTextContent(message)
})

const renderAt = (entry: string | { pathname: string; state?: unknown }, strict = false) => {
  const app = <RouterProvider router={createMemoryRouter(routes, { initialEntries: [entry] })} />
  return render(strict ? <StrictMode>{app}</StrictMode> : app)
}

test('a successful sign-in stores the token and opens the app', async () => {
  signedOut()
  fetchMock.mockResolvedValue(json(200, { data: { token: 'fresh' } }))
  renderAt({ pathname: '/sign-in', state: { from: '/' } })
  fireEvent.input(await screen.findByLabelText('Email'), { target: { value: 'aysel@example.test' } })
  fireEvent.input(screen.getByLabelText('Password'), { target: { value: 'secret-pass' } })
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('heading', { name: 'Welcome to Paygenix' })).toBeInTheDocument()
  expect(getAccessToken()).toBe('fresh')
})

test('return paths stay on this site', () => {
  expect(safeReturnPath('/cards/1')).toBe('/cards/1')
  expect(safeReturnPath('//evil.example')).toBe('/')
  expect(safeReturnPath('https://evil.example')).toBe('/')
  expect(safeReturnPath(undefined)).toBe('/')
})

test('the 401 retry resends the request body', async () => {
  signedIn('old')
  const bodies: string[] = []
  fetchMock.mockImplementation(async (input: RequestInfo | URL) => {
    if (urlOf(input).endsWith('/api/auth/refresh-token')) return json(200, { data: { token: 'new' } })
    const req = input as Request
    bodies.push(await req.text())
    return req.headers.get('Authorization') === 'Bearer new' ? json(200, {}) : json(401, {})
  })
  await api.POST('/api/auth/login', { body: { email: 'a@b.c', password: 'pw' } })
  expect(bodies).toEqual([bodies[0], bodies[0]])
  expect(JSON.parse(bodies[0]!)).toEqual({ email: 'a@b.c', password: 'pw' })
})

test('refresh runs under the cross-tab Web Lock', async () => {
  const request = vi.fn((_name: string, fn: () => Promise<unknown>) => fn())
  vi.stubGlobal('navigator', { ...navigator, locks: { request } })
  fetchMock.mockResolvedValue(json(200, { data: { token: 't' } }))
  await refresh()
  expect(request).toHaveBeenCalledWith('paygenix-refresh', expect.any(Function))
})

test('a refresh that finishes after a newer sign-in does not overwrite it', async () => {
  signedOut()
  let finish!: (r: Response) => void
  fetchMock.mockReturnValue(new Promise<Response>((r) => { finish = r }))
  const pending = refresh()
  signedIn('google-session')
  finish(json(200, { data: { token: 'old-session' } }))
  await pending
  expect(getAccessToken()).toBe('google-session')
})

test('sign-out revokes the current session, and signs out even if that fails', async () => {
  signedIn('t')
  fetchMock.mockImplementation(async (input: RequestInfo | URL) =>
    (input as Request).method === 'GET' ? json(200, { data: [{ id: 'a1b2', current: false }, { id: 'c3d4', current: true }] }) : json(200, {}))
  await signOut()
  const del = fetchMock.mock.calls.map(([r]) => r as Request).find((r) => r.method === 'DELETE')
  expect(del?.url).toMatch(/\/api\/auth\/sessions\/c3d4$/)
  expect(getAccessToken()).toBeNull()

  signedIn('t')
  fetchMock.mockRejectedValue(new TypeError('offline'))
  await signOut()
  expect(getAccessToken()).toBeNull()
})

test('the Google code is exchanged once, even under StrictMode', async () => {
  signedOut()
  fetchMock.mockResolvedValue(json(200, { data: { token: 'g' } }))
  renderAt('/oauth2/callback?code=one-time', true)
  expect(await screen.findByRole('heading', { name: 'Welcome to Paygenix' })).toBeInTheDocument()
  const exchanges = fetchMock.mock.calls.filter(([r]) => urlOf(r).endsWith('/api/auth/oauth/exchange'))
  expect(exchanges).toHaveLength(1)
})

test('Google errors are explained', async () => {
  signedOut()
  renderAt('/oauth2/callback?error=account_exists')
  expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists')
  expect(fetchMock).not.toHaveBeenCalled()
})
