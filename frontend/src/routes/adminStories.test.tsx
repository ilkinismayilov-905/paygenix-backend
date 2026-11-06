import { fireEvent, render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })

let live: { id: number; mediaType: string; mediaUrl: string; expiresAt: string; views: number }[]
let publish: () => Response
let remove: () => Response
const posted: { hours: string | null; size: number }[] = []
beforeEach(() => {
  posted.length = 0
  signedIn('session')
  live = [{ id: 5, mediaType: 'IMAGE', mediaUrl: 'https://s.test/5.jpg', expiresAt: '2026-11-07T10:00:00Z', views: 12 }]
  publish = () => json(201, { data: { id: 6 } })
  remove = () => ok(null)
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'admin', roles: ['ADMIN_ROLE'], pendingLegalDocuments: [] })
    if (url.pathname === '/api/admin/stories' && req.method === 'GET') return ok(live)
    if (url.pathname === '/api/admin/stories' && req.method === 'POST') {
      const f = (await req.clone().formData()).get('file') as Blob
      posted.push({ hours: url.searchParams.get('hours'), size: f.size })
      return publish()
    }
    if (url.pathname === '/api/admin/stories/5' && req.method === 'DELETE') return remove()
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/admin/stories'] })} />)
const file = (type: string, size = 100) => new File([new Uint8Array(size)], 'promo', { type })

test('publishes a file for the chosen time and reloads the live list', async () => {
  open()
  expect(await screen.findByText('12 views')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Image or video'), { target: { files: [file('video/mp4')] } })
  fireEvent.change(screen.getByLabelText('Shown for'), { target: { value: '72' } })
  live = [...live, { id: 6, mediaType: 'VIDEO', mediaUrl: 'https://s.test/6.mp4', expiresAt: '2026-11-09T10:00:00Z', views: 0 }]
  fireEvent.click(screen.getByRole('button', { name: 'Publish' }))
  expect(await screen.findByText(/Published/)).toBeInTheDocument()
  expect(posted).toEqual([{ hours: '72', size: 100 }])
  expect(await screen.findByText('0 views')).toBeInTheDocument()
})

test('refuses other files and big ones before sending', async () => {
  open()
  await screen.findByText('12 views')
  fireEvent.click(screen.getByRole('button', { name: 'Publish' }))
  expect(await screen.findByText('Choose an image or a video.')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Image or video'), { target: { files: [file('application/pdf')] } })
  expect(screen.getByText(/Use an image/)).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Image or video'), { target: { files: [file('image/png', 10 * 1024 * 1024 + 1)] } })
  expect(screen.getByText('The file is larger than 10 MB.')).toBeInTheDocument()
  expect(posted).toHaveLength(0)
})

test('take down asks first and removes the row', async () => {
  open()
  fireEvent.click(await screen.findByRole('button', { name: 'Take down' }))
  const dialog = await screen.findByRole('dialog')
  fireEvent.click(within(dialog).getByRole('button', { name: 'Take down' }))
  expect(await screen.findByText('The story was taken down.')).toBeInTheDocument()
  expect(screen.getByText('No promo story is live.')).toBeInTheDocument()
})
