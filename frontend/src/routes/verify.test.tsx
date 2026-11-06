import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'
import { multipartParts } from '../test/multipart'

const json = (status: number, body: unknown, headers: Record<string, string> = {}) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
const ok = (data: unknown) => json(200, { data })

let me: () => Response
let submit: () => Response
const sent: { type: string | null; file: { filename?: string; type?: string; size: number } | null }[] = []
beforeEach(() => {
  sent.length = 0
  signedIn('session')
  me = () => ok({ status: 'NOT_SUBMITTED' })
  submit = () => json(201, { data: { status: 'PENDING', documentType: 'PASSPORT', submittedAt: '2026-11-06T10:00:00Z' } })
  globalThis.URL.createObjectURL = () => 'blob:preview'
  globalThis.URL.revokeObjectURL = () => {}
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', roles: ['USER_ROLE'], pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/kyc/me') return me()
    if (url.pathname === '/api/v1/kyc' && req.method === 'POST') {
      const parts = await multipartParts(req)
      sent.push({ type: url.searchParams.get('documentType'), file: parts.document ?? null })
      return submit()
    }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/settings/verify'] })} />)
const photo = (type = 'image/jpeg', size = 1000) => new File([new Uint8Array(size)], 'id.jpg', { type })
const choose = (file: File) => fireEvent.change(screen.getByLabelText('Photo of the document'), { target: { files: [file] } })
const send = () => fireEvent.click(screen.getByRole('button', { name: 'Send for review' }))

test('sends the document type and photo, then shows it waits for review', async () => {
  open()
  fireEvent.click(await screen.findByLabelText('Passport'))
  choose(photo())
  expect(screen.getByAltText('The photo you chose')).toHaveAttribute('src', 'blob:preview')
  send()
  expect(await screen.findByText('We are checking your document.')).toBeInTheDocument()
  expect(sent).toHaveLength(1)
  expect(sent[0]!.type).toBe('PASSPORT')
  // the part is sent with its type; its bytes are not checked here (see test/multipart.ts)
  expect(sent[0]!.file).toMatchObject({ type: 'image/jpeg' })
})

test('type and photo are required; wrong or large photos are refused before sending', async () => {
  open()
  await screen.findByLabelText('Passport')
  send()
  expect(await screen.findByText('Choose a document type.')).toBeInTheDocument()
  expect(screen.getByText('Choose a photo of the document.')).toBeInTheDocument()
  choose(photo('application/pdf'))
  expect(screen.getByText('Use a JPEG, PNG or WebP photo.')).toBeInTheDocument()
  choose(photo('image/png', 5 * 1024 * 1024 + 1))
  expect(screen.getByText('The photo is larger than 5 MB. Choose a smaller one.')).toBeInTheDocument()
  expect(sent).toHaveLength(0)
})

test('a rejection shows the reason and lets the user send again', async () => {
  me = () => ok({ status: 'REJECTED', documentType: 'ID_CARD', rejectionReason: 'The photo is blurred.' })
  open()
  expect(await screen.findByText('The photo is blurred.')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Send for review' })).toBeInTheDocument()
})

test.each([
  ['PENDING', 'We are checking your document.'],
  ['APPROVED', 'Your identity is verified.'],
])('%s shows no form', async (status, text) => {
  me = () => ok({ status, submittedAt: '2026-11-06T10:00:00Z' })
  open()
  expect(await screen.findByText(text)).toBeInTheDocument()
  expect(screen.queryByRole('button', { name: 'Send for review' })).toBeNull()
})

test.each([
  [429, 'KYC_SUBMIT_LIMIT', { 'Retry-After': '7200' }, 'Too many documents sent today. Try again in 2 h.'],
  [503, 'MEDIA_UPLOAD_FAILED', {}, /could not store the photo/],
  [413, 'DOCUMENT_TOO_LARGE', {}, /larger than 5 MB/],
])('%i %s says so', async (status, code, headers, text) => {
  submit = () => json(status, { code }, headers)
  open()
  fireEvent.click(await screen.findByLabelText('ID card'))
  choose(photo())
  send()
  expect(await screen.findByText(text)).toBeInTheDocument()
})

test('already pending elsewhere reloads the status', async () => {
  submit = () => json(409, { code: 'KYC_ALREADY_PENDING' })
  open()
  fireEvent.click(await screen.findByLabelText('ID card'))
  choose(photo())
  me = () => ok({ status: 'PENDING', submittedAt: '2026-11-06T10:00:00Z' })
  send()
  expect(await screen.findByText('We are checking your document.')).toBeInTheDocument()
})
