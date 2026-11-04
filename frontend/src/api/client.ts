import createClient from 'openapi-fetch'
import type { paths } from './schema'

// shortcut: no refresh-on-401 yet; the single-flight refresh (ADR 0001) lands with the sign-in screen.
let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export const api = createClient<paths>({ baseUrl: '/' })

api.use({
  onRequest({ request }) {
    if (accessToken) request.headers.set('Authorization', `Bearer ${accessToken}`)
    return request
  },
})

/** RFC 9457 problem+json from the backend. The UI branches on `code`, never on `message`. */
export type Problem = { code?: string; title?: string; status?: number; detail?: string }
