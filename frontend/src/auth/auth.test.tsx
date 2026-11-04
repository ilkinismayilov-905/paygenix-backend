import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { api } from '../api/client'
import { routes } from '../router'
import { getAccessToken, refresh, signedIn, signedOut } from './session'

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
