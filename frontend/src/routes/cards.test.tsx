import { fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })
const card = (id: number, last4: string, amount = '0.00', currency = 'AZN') =>
  ({ id, maskedNumber: `**** **** **** ${last4}`, expirationDate: '2029-11', cardHolderName: 'NIGAR ALIYEVA', cardNetwork: 'VISA', currency, amount })

let mine: ReturnType<typeof card>[]
let issue: (body: { currency: string }) => Response
let one: (id: string) => Response
let close: () => Response
const activityAsked: (string | null)[] = []
beforeEach(() => {
  activityAsked.length = 0
  signedIn('session')
  mine = [card(7, '2207', '89.80')]
  issue = ({ currency }) => { const c = card(8, '8778', '0.00', currency); mine = [...mine, c]; return json(201, { data: { cardNumber: '4000001984948778', card: c } }) }
  one = (id) => { const c = mine.find((m) => String(m.id) === id); return c ? ok(c) : json(404, { code: 'NOT_FOUND' }) }
  close = () => ok(null)
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/cards/me') return ok(mine)
    if (url.pathname === '/api/cards' && req.method === 'POST') return issue(await req.clone().json())
    if (url.pathname === '/api/cards/me/activity') {
      activityAsked.push(url.searchParams.get('cardId'))
      return ok({ items: [{ id: 1, cardId: 7, last4: '2207', kind: 'TOP_UP', amount: '89.80', currency: 'AZN', createdAt: '2026-11-05T09:23:54Z' }], page: 0, size: 20, hasNext: false })
    }
    const m = /^\/api\/cards\/(\w+)$/.exec(url.pathname)
    if (m && req.method === 'GET') return one(m[1]!)
    if (m && req.method === 'DELETE') return close()
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = (path: string) => {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

test('issuing a card shows its full number once and adds it to the list', async () => {
  open('/cards')
  const list = await screen.findByRole('list', { name: 'Your cards' })
  expect(within(list).getAllByRole('link')).toHaveLength(1)
  fireEvent.change(screen.getByLabelText('Currency'), { target: { value: 'USD' } })
  fireEvent.click(screen.getByRole('button', { name: 'Issue a card' }))
  const dialog = await screen.findByRole('dialog', { name: 'Your new card' })
  expect(within(dialog).getByText('4000 0019 8494 8778')).toBeInTheDocument()
  fireEvent.click(within(dialog).getByRole('button', { name: 'Done' }))
  expect(screen.queryByText('4000 0019 8494 8778')).toBeNull()
  expect(await within(list).findByText('$0.00')).toBeInTheDocument()
})

test('a failed issue says so', async () => {
  issue = () => json(500, {})
  open('/cards')
  fireEvent.click(await screen.findByRole('button', { name: 'Issue a card' }))
  expect(await screen.findByText(/The card was not issued/)).toBeInTheDocument()
})

test('the detail shows the card and only its activity', async () => {
  open('/cards/7')
  expect(await screen.findByRole('heading', { name: 'Card ending in 2207' })).toBeInTheDocument()
  expect(await screen.findByText('+₼89.80')).toBeInTheDocument()
  expect(activityAsked).toContain('7')
})

test('a card with money cannot be closed', async () => {
  close = () => json(409, { code: 'CARD_HAS_BALANCE' })
  open('/cards/7')
  fireEvent.click(await screen.findByRole('button', { name: 'Close card' }))
  const dialog = await screen.findByRole('dialog', { name: 'Close this card?' })
  fireEvent.click(within(dialog).getByRole('button', { name: 'Close card' }))
  expect(await within(dialog).findByText(/Move the money to another card first/)).toBeInTheDocument()
})

test('closing an empty card goes back to the list', async () => {
  mine = [card(7, '2207')]
  close = () => { mine = []; return ok(null) }
  const router = open('/cards/7')
  fireEvent.click(await screen.findByRole('button', { name: 'Close card' }))
  fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Close card' }))
  expect(await screen.findByText('No cards yet')).toBeInTheDocument()
  expect(router.state.location.pathname).toBe('/cards')
})

test('an unknown or closed card says so', async () => {
  open('/cards/99')
  expect(await screen.findByText('This card does not exist or was closed.')).toBeInTheDocument()
})
