import { fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })

const user = { id: 5, username: 'nigar', email: 'n@example.test', fullName: 'Nigar A', status: 'ACTIVE', verified: true, roles: ['USER_ROLE'] }
const report = { id: 9, userId: 5, username: 'nigar', email: 'n@example.test', category: 'CARD_NOT_WORKING', message: 'Declined', referenceId: 'card 1234', status: 'OPEN', createdAt: '2026-11-06T10:00:00Z' }
const queries: string[] = []
let lock: () => Response
const patches: unknown[] = []
beforeEach(() => {
  queries.length = 0; patches.length = 0
  signedIn('session')
  lock = () => ok({ ...user, status: 'LOCKED' })
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'admin', roles: ['ADMIN_ROLE'], pendingLegalDocuments: [] })
    if (url.pathname === '/api/admin/users') { queries.push(url.searchParams.get('query') ?? ''); return ok({ items: [user], hasNext: false }) }
    if (url.pathname === '/api/admin/users/5/lock') return lock()
    if (url.pathname === '/api/admin/reports') return ok({ items: url.searchParams.get('status') === 'OPEN' ? [report] : [], hasNext: false })
    if (url.pathname === '/api/admin/reports/9') { patches.push(await req.clone().json()); return ok({ ...report, status: 'RESOLVED' }) }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = (path: string) => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />)

test('finds users and locks one after a confirm', async () => {
  open('/admin/users')
  fireEvent.change(await screen.findByLabelText('Email or username'), { target: { value: ' nig ' } })
  fireEvent.click(screen.getByRole('button', { name: 'Find' }))
  await vi.waitFor(() => expect(queries.at(-1)).toBe('nig'))
  fireEvent.click(await screen.findByRole('button', { name: 'Lock nigar' }))
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'Lock @nigar?' })).getByRole('button', { name: 'Lock' }))
  expect(await screen.findByText('@nigar is locked.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Unlock nigar' })).toBeInTheDocument()
})

test('a refused lock says why and keeps the dialog', async () => {
  lock = () => json(403, { code: 'ADMIN_TARGET' })
  open('/admin/users')
  fireEvent.click(await screen.findByRole('button', { name: 'Lock nigar' }))
  const dialog = await screen.findByRole('dialog')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Lock' }))
  expect(await within(dialog).findByText('Admins cannot be locked here.')).toBeInTheDocument()
})

test('a report moved to another status leaves the tab', async () => {
  open('/admin/reports')
  expect(await screen.findByText('Declined')).toBeInTheDocument()
  expect(screen.getByText('Reference: card 1234')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'RESOLVED' } })
  expect(await screen.findByText('No reports here.')).toBeInTheDocument()
  expect(patches).toEqual([{ status: 'RESOLVED' }])
  expect(screen.getByRole('link', { name: 'Users' })).toHaveAttribute('href', '/admin/users')
})
