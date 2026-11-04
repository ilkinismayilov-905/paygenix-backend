import createClient from 'openapi-fetch'
import { getAccessToken, refresh } from '../auth/session'
import type { paths } from './schema'

/**
 * Adds the access token. On a 401 for a request that carried one, it waits for the single shared refresh and
 * retries once; if the refresh fails, the session store flips to signed-out and the router sends the user to sign-in.
 */
async function authFetch(request: Request): Promise<Response> {
  const send = (token: string | null) => {
    const r = request.clone() // the original stays unread, so the retry can resend the body
    if (token) r.headers.set('Authorization', `Bearer ${token}`)
    return fetch(r)
  }
  const token = getAccessToken()
  const res = await send(token)
  if (res.status !== 401 || !token) return res
  return (await refresh()) ? send(getAccessToken()) : res
}

// Absolute base: same origin as the page (ADR 0001), and Request objects need a full URL outside the browser.
export const api = createClient<paths>({ baseUrl: globalThis.location?.origin ?? '', fetch: authFetch })

/** RFC 9457 problem+json from the backend. The UI branches on `code`, never on `message`. */
export type Problem = { code?: string; title?: string; status?: number; detail?: string }

/** Retry-After in whole minutes (at least 1), or null when the header is missing. */
export function retryAfterMinutes(res: Response): number | null {
  const s = Number(res.headers.get('Retry-After'))
  return Number.isFinite(s) && s > 0 ? Math.max(1, Math.ceil(s / 60)) : null
}
