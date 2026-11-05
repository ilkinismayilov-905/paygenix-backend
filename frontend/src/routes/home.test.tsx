import { fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })
const card = { id: 7, maskedNumber: '**** **** **** 2207', expirationDate: '09/29', cardHolderName: 'Nigar Aliyeva', cardNetwork: 'VISA', currency: 'AZN', amount: '89.80' }
const item = (id: number, amount: string, kind = 'TRANSFER') =>
  ({ id, cardId: 7, last4: '2207', kind, amount, currency: 'AZN', createdAt: '2026-11-05T09:23:54Z' })

let activity: (page: string | null) => Response
let cards: () => Response
beforeEach(() => {
  signedIn('session')
  cards = () => ok([card])
  activity = () => ok({ items: [item(2, '-10.20')], page: 0, size: 20, hasNext: false })
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/cards/me') return cards()
    if (url.pathname === '/api/cards/me/activity') return activity(url.searchParams.get('page'))
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const renderHome = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/'] })} />)

test('shows the cards and signed activity amounts from raw decimal strings', async () => {
  renderHome()
  expect(await screen.findByRole('heading', { name: 'Welcome to Paygenix' })).toBeInTheDocument()
  expect(within(await screen.findByRole('group', { name: 'Your cards' })).getByText('₼89.80')).toBeInTheDocument()
  expect(await screen.findByText('-₼10.20')).toBeInTheDocument()
  expect(screen.getByText('Transfer')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Show more' })).toBeNull()
})

test('show more appends the next page until there is none', async () => {
  activity = (page) => page === '1'
    ? ok({ items: [item(1, '100.00', 'TOP_UP')], page: 1, size: 20, hasNext: false })
    : ok({ items: [item(2, '-10.20')], page: 0, size: 20, hasNext: true })
  renderHome()
  fireEvent.click(await screen.findByRole('button', { name: 'Show more' }))
  expect(await screen.findByText('+₼100.00')).toBeInTheDocument()
  expect(screen.getByText('-₼10.20')).toBeInTheDocument()
  expect(screen.getByText('Top-up')).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Show more' })).toBeNull()
})

test('empty account shows empty states', async () => {
  cards = () => ok([])
  activity = () => ok({ items: [], page: 0, size: 20, hasNext: false })
  renderHome()
  expect(await screen.findByText('No cards yet')).toBeInTheDocument()
  expect(await screen.findByText('No activity yet')).toBeInTheDocument()
})

test('a failed load offers a retry that recovers', async () => {
  cards = () => json(503, { code: 'SERVICE_UNAVAILABLE' })
  renderHome()
  const retry = await screen.findByRole('button', { name: 'Try again' })
  cards = () => ok([card])
  fireEvent.click(retry)
  expect(await screen.findByRole('group', { name: 'Your cards' })).toBeInTheDocument()
})
