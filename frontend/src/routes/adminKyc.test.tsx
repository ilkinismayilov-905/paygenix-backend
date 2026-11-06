import { fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })

const item = (id: number, extra: object = {}) => ({
  id, userId: 10 + id, username: `user${id}`, fullName: `Person ${id}`, email: `u${id}@example.test`,
  documentType: 'PASSPORT', status: 'PENDING', submittedAt: '2026-11-06T10:00:00Z', ...extra,
})

let roles: string[]
let list: (status: string, page: number) => Response
let detail: () => Response
let decide: () => Response
const decisions: { path: string; body: unknown }[] = []
beforeEach(() => {
  decisions.length = 0
  signedIn('session')
  roles = ['USER_ROLE', 'ADMIN_ROLE']
  list = (status, page) => ok({ items: status === 'PENDING' ? (page === 0 ? [item(1), item(2)] : [item(3)]) : [], page, size: 20, hasNext: status === 'PENDING' && page === 0 })
  detail = () => ok({ ...item(1), documentUrl: 'https://storage.test/kyc/1.jpg?sig=x' })
  decide = () => ok(item(1, { status: 'APPROVED' }))
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'admin', roles, pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/admin/kyc') return list(url.searchParams.get('status')!, Number(url.searchParams.get('page')))
    if (/^\/api\/v1\/admin\/kyc\/\d+$/.test(url.pathname)) return detail()
    if (/^\/api\/v1\/admin\/kyc\/\d+\/(approve|reject)$/.test(url.pathname)) {
      decisions.push({ path: url.pathname, body: req.headers.get('Content-Type')?.includes('json') ? await req.clone().json() : null })
      return decide()
    }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = (path: string) => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />)

test('admins see the Review link and pending documents, with more on request', async () => {
  open('/admin/kyc')
  expect(await screen.findByRole('link', { name: 'Review' })).toHaveAttribute('href', '/admin/kyc')
  expect(await screen.findByRole('link', { name: 'Person 1' })).toHaveAttribute('href', '/admin/kyc/1')
  fireEvent.click(screen.getByRole('button', { name: 'Show more' }))
  expect(await screen.findByRole('link', { name: 'Person 3' })).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Show more' })).toBeNull()
  fireEvent.click(screen.getByRole('button', { name: 'Approved' }))
  expect(await screen.findByText('No reviews here yet.')).toBeInTheDocument()
})

test('others get the not-found page and no Review link', async () => {
  roles = ['USER_ROLE']
  open('/admin/kyc')
  expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  expect(screen.queryByRole('link', { name: 'Review' })).toBeNull()
})

test('the notice is shown once: the history entry forgets it', async () => {
  const router = createMemoryRouter(routes, { initialEntries: [{ pathname: '/admin/kyc', state: { reviewed: 'Person 9 is verified.' } }] })
  render(<RouterProvider router={router} />)
  expect(await screen.findByText('Person 9 is verified.')).toBeInTheDocument()
  await vi.waitFor(() => expect(router.state.location.state).toBeNull())
})

test('approve asks first, then goes back to the list with a notice', async () => {
  open('/admin/kyc/1')
  expect(await screen.findByAltText('Identity document of Person 1')).toHaveAttribute('src', 'https://storage.test/kyc/1.jpg?sig=x')
  fireEvent.click(screen.getByRole('button', { name: 'Approve' }))
  fireEvent.click(within(await screen.findByRole('dialog', { name: 'Approve this document?' })).getByRole('button', { name: 'Approve' }))
  expect(await screen.findByText('Person 1 is verified.')).toBeInTheDocument()
  expect(decisions).toEqual([{ path: '/api/v1/admin/kyc/1/approve', body: null }])
})

test('reject needs a reason, sends it trimmed', async () => {
  decide = () => ok(item(1, { status: 'REJECTED' }))
  open('/admin/kyc/1')
  fireEvent.click(await screen.findByRole('button', { name: 'Reject' }))
  const dialog = await screen.findByRole('dialog')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Reject' }))
  expect(await within(dialog).findByText('Write a reason.')).toBeInTheDocument()
  fireEvent.change(within(dialog).getByLabelText('Reason for the user'), { target: { value: '  The photo is blurred.  ' } })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Reject' }))
  expect(await screen.findByText('The document of Person 1 was rejected.')).toBeInTheDocument()
  expect(decisions).toEqual([{ path: '/api/v1/admin/kyc/1/reject', body: { reason: 'The photo is blurred.' } }])
})

test('own submission and an earlier review say so', async () => {
  decide = () => json(403, { code: 'KYC_OWN_SUBMISSION' })
  open('/admin/kyc/1')
  const approve = async () => {
    fireEvent.click(screen.getByRole('button', { name: 'Approve' }))
    fireEvent.click(within(await screen.findByRole('dialog', { name: 'Approve this document?' })).getByRole('button', { name: 'Approve' }))
  }
  await screen.findByRole('button', { name: 'Approve' })
  await approve()
  expect(await screen.findByText('You cannot review your own document.')).toBeInTheDocument()
  decide = () => json(409, { code: 'KYC_ALREADY_REVIEWED' })
  detail = () => ok({ ...item(1, { status: 'APPROVED', reviewedAt: '2026-11-06T11:00:00Z' }), documentUrl: 'https://storage.test/x' })
  await approve()
  expect(await screen.findByText('Someone already reviewed this document.')).toBeInTheDocument()
  expect(await screen.findByText('Approved')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Approve' })).toBeNull()
})

test('a photo that fails to load offers a new link', async () => {
  open('/admin/kyc/1')
  fireEvent.error(await screen.findByAltText('Identity document of Person 1'))
  expect(await screen.findByText(/Could not load the photo/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByAltText('Identity document of Person 1')).toBeInTheDocument()
})
