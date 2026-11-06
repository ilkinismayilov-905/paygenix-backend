import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })
const saved = { fullName: 'Nigar Aliyeva', email: 'nigar@example.test', phoneNumber: '+994501234567', dateOfBirth: '02-03-1995', gender: 'FEMALE', objectName: null }

let me: () => Response
let save: () => Response
const posted: unknown[] = []
beforeEach(() => {
  posted.length = 0
  signedIn('session')
  me = () => ok(saved)
  save = () => json(201, { data: saved })
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/profile/me') return me()
    if (url.pathname === '/api/v1/profile' && req.method === 'POST') { posted.push(await req.clone().json()); return save() }
    if (url.pathname === '/api/v1/cards/me') return ok([])
    if (url.pathname === '/api/v1/cards/me/activity') return ok({ items: [], page: 0, size: 20, hasNext: false })
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = (path = '/settings/profile') => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />)
const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Save' }))

test('prefills the saved profile and sends it back in the API formats', async () => {
  open()
  expect(await screen.findByLabelText('Full name')).toHaveValue('Nigar Aliyeva')
  expect(screen.getByLabelText('Email')).toHaveValue('nigar@example.test')
  expect(screen.getByLabelText('Date of birth')).toHaveValue('1995-03-02')
  expect(screen.getByLabelText('Female')).toBeChecked()
  fireEvent.change(screen.getByLabelText('Phone number'), { target: { value: '055 111 22 33' } })
  submit()
  expect(await screen.findByText('Profile saved.')).toBeInTheDocument()
  expect(posted[0]).toEqual({ fullName: 'Nigar Aliyeva', phoneNumber: '+994551112233', dateOfBirth: '02-03-1995', gender: 'FEMALE' })
})

test('a fresh account must fill every field before the first save', async () => {
  me = () => ok({ fullName: null, email: 'new@example.test' })
  open()
  await screen.findByLabelText('Full name')
  submit()
  expect(await screen.findAllByText('This field is required.')).toHaveLength(3) // name, date, gender
  expect(screen.getByText(/Enter an Azerbaijani number/)).toBeInTheDocument()
  expect(posted).toHaveLength(0)
})

test('a rejected save says so', async () => {
  save = () => json(400, { code: 'VALIDATION_FAILED' })
  open()
  await screen.findByLabelText('Full name')
  submit()
  expect(await screen.findByText(/were not accepted/)).toBeInTheDocument()
})

test('the dashboard greets by first name', async () => {
  open('/')
  expect(await screen.findByRole('heading', { name: 'Hi, Nigar' })).toBeInTheDocument()
})

test('a date of birth that is today or later is refused', async () => {
  open()
  fireEvent.change(await screen.findByLabelText('Date of birth'), { target: { value: new Date().toLocaleDateString('sv') } })
  submit()
  expect(await screen.findByText('Date of birth must be in the past.')).toBeInTheDocument()
  expect(posted).toHaveLength(0)
})

test('adds a photo, shows it, and removes it', async () => {
  let photo: string | null = null
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/v1/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/v1/profile/me') return ok({ ...saved, photoUrl: photo })
    if (url.pathname === '/api/v1/profile/photo' && req.method === 'POST') { photo = 'https://s.test/me.png'; return ok({ ...saved, photoUrl: photo }) }
    if (url.pathname === '/api/v1/profile/photo' && req.method === 'DELETE') { photo = null; return ok(null) }
    return json(404, {})
  })
  open()
  const input = await screen.findByLabelText('Add photo')
  fireEvent.change(input, { target: { files: [new File([new Uint8Array(10)], 'me.png', { type: 'image/png' })] } })
  expect(await screen.findByAltText('Your profile photo')).toHaveAttribute('src', 'https://s.test/me.png')
  fireEvent.click(screen.getByRole('button', { name: 'Remove photo' }))
  await vi.waitFor(() => expect(screen.queryByAltText('Your profile photo')).toBeNull())
  expect(screen.getByLabelText('Add photo')).toBeInTheDocument()
})

test('a photo needs a saved profile; wrong files are refused before sending', async () => {
  me = () => ok({ fullName: 'Nigar', email: 'nigar@example.test' })
  open()
  expect(await screen.findByText('Save your profile first, then add a photo.')).toBeInTheDocument()
  expect(screen.getByLabelText('Add photo')).toBeDisabled()
})
