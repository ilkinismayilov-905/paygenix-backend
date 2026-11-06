import { createBrowserRouter } from 'react-router'
import { PublicOnly, RequireAdmin, RequireAuth } from './routes/guards'
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
      // readable signed in or not, and before pending Terms are accepted
      { path: '/legal/terms', lazy: async () => ({ Component: (await import('./routes/LegalPage')).Terms }) },
      { path: '/legal/privacy', lazy: async () => ({ Component: (await import('./routes/LegalPage')).Privacy }) },
      {
        element: <RequireAuth />,
        children: [{
          lazy: page(() => import('./routes/AppShell')),
          children: [
            { path: '/', lazy: page(() => import('./routes/Home')) },
            { path: '/cards', lazy: page(() => import('./routes/Cards')) },
            { path: '/cards/:id', lazy: page(() => import('./routes/CardDetail')) },
            { path: '/transfer', lazy: page(() => import('./routes/Transfer')) },
            { path: '/pay', lazy: page(() => import('./routes/Pay')) },
            { path: '/settings/profile', lazy: page(() => import('./routes/Profile')) },
            { path: '/pin/setup', lazy: page(() => import('./routes/PinSetup')) },
            { path: '/settings/pin', lazy: page(() => import('./routes/ChangePin')) },
            { path: '/settings/email', lazy: page(() => import('./routes/ChangeEmail')) },
            { path: '/settings/sessions', lazy: page(() => import('./routes/Sessions')) },
            { path: '/help/report', lazy: page(() => import('./routes/ReportProblem')) },
            { path: '/settings/verify', lazy: page(() => import('./routes/Verify')) },
            {
              element: <RequireAdmin />,
              children: [
                { path: '/admin/kyc', lazy: page(() => import('./routes/AdminKyc')) },
                { path: '/admin/kyc/:id', lazy: page(() => import('./routes/AdminKycReview')) },
              ],
            },
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
