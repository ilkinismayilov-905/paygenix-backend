import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { expect, test } from 'vitest'
import { routes } from '../router'

test('unknown paths render the not-found page', async () => {
  render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/nope'] })} />)
  expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
})
