import { fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { getAccessToken, signedIn, signedOut } from '../auth/session'
import { routes } from '../router'
import { deviceLabel } from './device'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })

const CHROME_WIN = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0 Safari/537.36'
const SAFARI_IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1'
const list = [
  { id: 'a', deviceName: CHROME_WIN, createdAt: '2026-11-01T10:00:00Z', lastUsedAt: '2026-11-06T09:00:00Z', current: true },
  { id: 'b', deviceName: SAFARI_IPHONE, createdAt: '2026-10-20T10:00:00Z', lastUsedAt: '2026-11-02T09:00:00Z', current: false },
]

let sessions: () => Response
let revokeOne: () => Response
let revokeAll: () => Response
const deleted: string[] = []
beforeEach(() => {
  deleted.length = 0
  signedIn('session')
  sessions = () => ok(list)
  revokeOne = () => ok(null)
  revokeAll = () => ok(null)
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/auth/sessions' && req.method === 'GET') return sessions()
    if (url.pathname === '/api/v1/auth/sessions' && req.method === 'DELETE') { deleted.push('all'); return revokeAll() }
    if (url.pathname.startsWith('/api/v1/auth/sessions/') && req.method === 'DELETE') { deleted.push(url.pathname.split('/').pop()!); return revokeOne() }
    if (url.pathname === '/api/v1/auth/logout') return ok(null)
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/settings/sessions'] })} />)

test('names devices from the user agent', () => {
  expect(deviceLabel(CHROME_WIN)).toBe('Chrome on Windows')
  expect(deviceLabel(SAFARI_IPHONE)).toBe('Safari on iPhone')
  expect(deviceLabel('Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/140.0 Safari/537.36 Edg/140.0')).toBe('Edge on Windows')
  expect(deviceLabel('Mozilla/5.0 (Linux; Android 15) Gecko/140.0 Firefox/140.0')).toBe('Firefox on Android')
  expect(deviceLabel('curl/8.0')).toBe('curl/8.0')
  expect(deviceLabel(null)).toBe('Unknown device')
})

test('lists devices, marks this one, and signs another out', async () => {
  open()
  const rows = await screen.findAllByRole('listitem')
  expect(rows).toHaveLength(2)
  const [current, other] = rows as [HTMLElement, HTMLElement]
  expect(within(current).getByText('This device')).toBeInTheDocument()
  expect(within(current).queryByRole('button')).toBeNull()
  fireEvent.click(within(other).getByRole('button', { name: 'Sign out Safari on iPhone' }))
  expect(await screen.findByText('Safari on iPhone is signed out.')).toBeInTheDocument()
  expect(screen.getAllByRole('listitem')).toHaveLength(1)
  expect(deleted).toEqual(['b'])
})

test('a device already gone counts as signed out', async () => {
  revokeOne = () => json(404, { code: 'NOT_FOUND' })
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Sign out Safari on iPhone' }))
  expect(await screen.findByText('Safari on iPhone is signed out.')).toBeInTheDocument()
  expect(screen.getAllByRole('listitem')).toHaveLength(1)
})

test('a failed revoke keeps the row', async () => {
  revokeOne = () => json(500, {})
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Sign out Safari on iPhone' }))
  expect(await screen.findByText(/Could not sign that device out/)).toBeInTheDocument()
  expect(screen.getAllByRole('listitem')).toHaveLength(2)
})

test('sign out everywhere asks first, then signs this tab out', async () => {
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Sign out everywhere' }))
  const dialog = await screen.findByRole('dialog')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Sign out everywhere' }))
  expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  expect(deleted).toEqual(['all'])
  expect(getAccessToken()).toBeNull()
})

test('a failed sign out everywhere stays signed in and says so', async () => {
  revokeAll = () => json(500, {})
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Sign out everywhere' }))
  const dialog = await screen.findByRole('dialog')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Sign out everywhere' }))
  expect(await within(dialog).findByText(/Could not sign out everywhere/)).toBeInTheDocument()
  expect(getAccessToken()).toBe('session')
})

test('a failed load offers a retry', async () => {
  sessions = () => json(500, {})
  open()
  expect(await screen.findByText('Could not load your devices.')).toBeInTheDocument()
  sessions = () => ok(list)
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findAllByRole('listitem')).toHaveLength(2)
})
