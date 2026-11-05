import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const me = (pending: unknown[]) => json(200, { data: { id: 1, username: 'nigar', pendingLegalDocuments: pending } })

let fetchMock: ReturnType<typeof vi.fn>
beforeEach(() => { fetchMock = vi.fn(); vi.stubGlobal('fetch', fetchMock); signedIn('session') })
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const renderHome = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/'] })} />)
const path = (r: Request) => new URL(r.url).pathname

test('a new Terms version is accepted before the app opens', async () => {
  const sent: unknown[] = []
  fetchMock.mockImplementation(async (req: Request) => {
    if (path(req) === '/api/auth/me') return me([{ type: 'TERMS', version: '2' }])
    if (path(req) === '/api/legal/terms') return json(200, { data: { type: 'TERMS', version: '2', content: 'New terms' } })
    if (path(req) === '/api/legal/acceptances') { sent.push(await req.json()); return json(200, { data: null }) }
    return json(200, { data: { type: 'PRIVACY', version: '1', content: '' } })
  })
  renderHome()
  expect(await screen.findByRole('heading', { name: 'Updated Terms' })).toBeInTheDocument()
  expect(screen.queryByRole('heading', { name: 'Welcome to Paygenix' })).toBeNull()

  fireEvent.click(screen.getByRole('button', { name: 'Terms' }))
  expect(await screen.findByText('New terms')).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: 'Close' }))

  fireEvent.click(screen.getByRole('button', { name: 'Accept and continue' }))
  expect(await screen.findByRole('heading', { name: 'Welcome to Paygenix' })).toBeInTheDocument()
  expect(sent).toEqual([{ documents: [{ type: 'TERMS', version: '2' }] }])
})

test('a version published while accepting is shown instead', async () => {
  let accepts = 0
  fetchMock.mockImplementation(async (req: Request) => {
    if (path(req) === '/api/auth/me') return me(accepts === 0 ? [{ type: 'TERMS', version: '2' }] : [{ type: 'TERMS', version: '3' }])
    if (path(req) === '/api/legal/acceptances') { accepts++; return json(409, { code: 'LEGAL_VERSION_OUTDATED' }) }
    return json(404, {})
  })
  renderHome()
  fireEvent.click(await screen.findByRole('button', { name: 'Accept and continue' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('A newer version was just published')
  expect(screen.getByText('(version 3)')).toBeInTheDocument()
})

test('when the check fails the app still opens (the backend keeps blocking payments)', async () => {
  fetchMock.mockRejectedValue(new TypeError('offline'))
  renderHome()
  expect(await screen.findByRole('heading', { name: 'Welcome to Paygenix' })).toBeInTheDocument()
})
