import { act, fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })

const story = (id: number, extra: object = {}) => ({ storyId: id, mediaType: 'IMAGE', mediaUrl: `https://s.test/${id}.jpg`, viewed: false, ...extra })
let feed: () => Response
const viewed: string[] = []
beforeEach(() => {
  viewed.length = 0
  signedIn('session')
  feed = () => ok({ groups: [{ username: 'Paygenix', hasUnseen: true, stories: [story(1, { viewed: true }), story(2), story(3, { mediaType: 'VIDEO', mediaUrl: 'https://s.test/3.mp4' })] }] })
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'nigar', roles: ['USER_ROLE'], pendingLegalDocuments: [] })
    if (url.pathname === '/api/stories/feed') return feed()
    if (/^\/api\/stories\/\d+\/view$/.test(url.pathname)) { viewed.push(url.pathname.split('/')[3]!); return ok(null) }
    if (url.pathname === '/api/cards/me') return ok([])
    if (url.pathname === '/api/cards/me/activity') return ok({ items: [], page: 0, size: 20, hasNext: false })
    return json(404, {})
  })
})
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); signedOut() })

const open = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/'] })} />)

test('opens at the first unseen story, records views, and moves on', async () => {
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Open new stories from Paygenix' }))
  const viewer = await screen.findByRole('dialog', { name: 'Stories from Paygenix, 2 of 3' })
  expect(within(viewer).getByAltText('Story from Paygenix')).toHaveAttribute('src', 'https://s.test/2.jpg')
  await vi.waitFor(() => expect(viewed).toEqual(['2']))
  fireEvent.click(within(viewer).getByRole('button', { name: 'Next story' }))
  expect(await screen.findByRole('dialog', { name: 'Stories from Paygenix, 3 of 3' })).toBeInTheDocument()
  expect(viewer.querySelector('video')).toHaveAttribute('src', 'https://s.test/3.mp4')
  await vi.waitFor(() => expect(viewed).toEqual(['2', '3']))
  fireEvent.click(within(viewer).getByRole('button', { name: 'Previous story' }))
  expect(await screen.findByRole('dialog', { name: 'Stories from Paygenix, 2 of 3' })).toBeInTheDocument()
  expect(viewed).toEqual(['2', '3']) // already seen: not sent again
})

test('after the last story the viewer closes and the ring shows all seen', async () => {
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Open new stories from Paygenix' }))
  const viewer = await screen.findByRole('dialog')
  fireEvent.click(within(viewer).getByRole('button', { name: 'Next story' }))
  await screen.findByRole('dialog', { name: 'Stories from Paygenix, 3 of 3' })
  fireEvent.click(within(viewer).getByRole('button', { name: 'Last story: close' }))
  await vi.waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(await screen.findByRole('button', { name: 'Open stories from Paygenix' })).toBeInTheDocument()
})

test('images move on by themselves after 5 seconds', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  feed = () => ok({ groups: [{ username: 'Paygenix', stories: [story(1), story(2)] }] })
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Open new stories from Paygenix' }))
  await screen.findByRole('dialog', { name: 'Stories from Paygenix, 1 of 2' })
  await act(async () => { vi.advanceTimersByTime(5000) })
  expect(await screen.findByRole('dialog', { name: 'Stories from Paygenix, 2 of 2' })).toBeInTheDocument()
})

test('no stories or a failed feed shows no row', async () => {
  feed = () => ok({ groups: [] })
  open()
  await screen.findByRole('heading', { name: 'Welcome to Paygenix' })
  expect(screen.queryByRole('button', { name: /stories from/ })).toBeNull()
})
