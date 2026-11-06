import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { signedIn, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const ok = (data: unknown) => json(200, { data })

let report: () => Response | Promise<Response>
const posted: unknown[] = []
beforeEach(() => {
  posted.length = 0
  signedIn('session')
  report = () => json(201, { data: { id: 42, status: 'OPEN' } })
  vi.stubGlobal('fetch', async (req: Request) => {
    const url = new URL(req.url)
    if (url.pathname === '/api/auth/me') return ok({ id: 1, username: 'nigar', pendingLegalDocuments: [] })
    if (url.pathname === '/api/reports' && req.method === 'POST') { posted.push(await req.clone().json()); return report() }
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = () => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/help/report'] })} />)
const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Send report' }))

test('sends the category, trimmed message and reference, then shows the report number', async () => {
  open()
  fireEvent.change(await screen.findByLabelText('What went wrong?'), { target: { value: 'INCORRECT_AMOUNT_CHARGED' } })
  fireEvent.change(screen.getByLabelText('Tell us what happened'), { target: { value: '  Charged twice  ' } })
  expect(screen.getByText('17 / 2000')).toBeInTheDocument()
  fireEvent.change(screen.getByLabelText('Reference (optional)'), { target: { value: ' card 1234 ' } })
  submit()
  expect(await screen.findByRole('heading', { name: 'Thanks, we got your report.' })).toHaveFocus()
  expect(screen.getByText('Report number 42. We will reply by email.')).toBeInTheDocument()
  expect(posted).toEqual([{ category: 'INCORRECT_AMOUNT_CHARGED', message: 'Charged twice', referenceId: 'card 1234' }])
})

test('an empty reference is left out; missing fields are flagged before sending', async () => {
  open()
  await screen.findByLabelText('What went wrong?')
  submit()
  expect(await screen.findByText('Choose what went wrong.')).toBeInTheDocument()
  expect(screen.getByText('Describe the problem.')).toBeInTheDocument()
  expect(posted).toHaveLength(0)
  fireEvent.change(screen.getByLabelText('What went wrong?'), { target: { value: 'SECURITY_CONCERN' } })
  fireEvent.change(screen.getByLabelText('Tell us what happened'), { target: { value: 'Odd sign-in' } })
  submit()
  await screen.findByRole('heading', { name: 'Thanks, we got your report.' })
  expect(posted).toEqual([{ category: 'SECURITY_CONCERN', message: 'Odd sign-in' }])
})

test('a failed send keeps the form and says so', async () => {
  report = () => { throw new TypeError('offline') }
  open()
  fireEvent.change(await screen.findByLabelText('What went wrong?'), { target: { value: 'CARD_NOT_WORKING' } })
  fireEvent.change(screen.getByLabelText('Tell us what happened'), { target: { value: 'Declined' } })
  submit()
  expect(await screen.findByText(/The report was not sent/)).toBeInTheDocument()
  expect(screen.getByLabelText('Tell us what happened')).toHaveValue('Declined')
})
