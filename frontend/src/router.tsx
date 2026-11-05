import { createBrowserRouter } from 'react-router'
import { PublicOnly, RequireAuth } from './routes/guards'
import RouteError from './routes/RouteError'

const page = (load: () => Promise<{ default: React.ComponentType }>) => async () => ({ Component: (await load()).default })

// Each page is its own chunk.
export const routes = [
  {
    errorElement: <RouteError />,
    children: [
      {
        element: <PublicOnly />,
        children: [
          { path: '/sign-in', lazy: page(() => import('./routes/SignIn')) },
          { path: '/sign-up', lazy: page(() => import('./routes/SignUp')) },
        ],
      },
      { path: '/oauth2/callback', lazy: page(() => import('./routes/OAuthCallback')) },
      {
        element: <RequireAuth />,
        children: [{
          lazy: page(() => import('./routes/AppShell')),
          children: [
            { path: '/', lazy: page(() => import('./routes/Home')) },
            { path: '/transfer', lazy: page(() => import('./routes/Transfer')) },
            { path: '/pay', lazy: page(() => import('./routes/Pay')) },
            { path: '/settings/profile', lazy: page(() => import('./routes/Profile')) },
          ],
        }],
      },
      // shortcut: the design preview stays reachable without sign-in; remove it once the screens are done.
      { path: '/design', lazy: page(() => import('./routes/DesignPreview')) },
      { path: '*', lazy: page(() => import('./routes/NotFound')) },
    ],
  },
]

export const router = createBrowserRouter(routes)
