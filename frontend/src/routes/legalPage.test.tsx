import { fireEvent, render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { refresh, signedOut } from '../auth/session'
import { routes } from '../router'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

let terms: () => Response
beforeEach(() => {
  terms = () => json(200, { data: { type: 'TERMS', version: '3', effectiveDate: '2026-11-05', content: 'Be nice.' } })
  vi.stubGlobal('fetch', async (input: Request | string) => {
    const url = new URL(typeof input === 'string' ? `http://localhost${input}` : input.url)
    if (url.pathname === '/api/v1/auth/refresh-token') return json(401, { code: 'INVALID_REFRESH_TOKEN' })
    if (url.pathname === '/api/v1/legal/terms') return terms()
    if (url.pathname === '/api/v1/legal/privacy') return json(404, { code: 'NOT_FOUND' })
    return json(404, {})
  })
})
afterEach(() => { vi.unstubAllGlobals(); signedOut() })

const open = (path: string) => render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />)

test('terms open without signing in, with version and date', async () => {
  await refresh() // no session
  open('/legal/terms')
  expect(await screen.findByText('Be nice.')).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'Terms' })).toBeInTheDocument()
  expect(screen.getByText(/version 3 · in effect since November 5, 2026/)).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'Privacy Policy' })).toHaveAttribute('href', '/legal/privacy')
})

test('an unpublished document says so', async () => {
  open('/legal/privacy')
  expect(await screen.findByText('This document is not published yet.')).toBeInTheDocument()
})

test('a failed load offers a retry', async () => {
  terms = () => json(500, {})
  open('/legal/terms')
  expect(await screen.findByText('Could not load this document.')).toBeInTheDocument()
  terms = () => json(200, { data: { type: 'TERMS', version: '3', content: 'Be nice.' } })
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByText('Be nice.')).toBeInTheDocument()
})
