import { createBrowserRouter } from 'react-router'
import RouteError from './routes/RouteError'

// Each route is its own chunk.
export const router = createBrowserRouter([
  {
    errorElement: <RouteError />,
    children: [
      { path: '/', lazy: async () => ({ Component: (await import('./routes/Home')).default }) },
      { path: '*', lazy: async () => ({ Component: (await import('./routes/NotFound')).default }) },
    ],
  },
])
