import { useSyncExternalStore } from 'react'

/**
 * Who is signed in, kept in memory only (ADR 0001): the access token never touches storage,
 * and the refresh token lives in an httpOnly cookie the page cannot read.
 */
export type AuthStatus = 'unknown' | 'signed-in' | 'signed-out'

let accessToken: string | null = null
let status: AuthStatus = 'unknown'
// Bumped by every sign-in and sign-out, so a refresh that started earlier cannot overwrite a newer session.
let epoch = 0
const listeners = new Set<() => void>()

function set(token: string | null, next: AuthStatus) {
  accessToken = token
  status = next
  epoch++
  listeners.forEach((l) => l())
}

export const getAccessToken = () => accessToken

export function useAuthStatus(): AuthStatus {
  return useSyncExternalStore((l) => { listeners.add(l); return () => listeners.delete(l) }, () => status)
}

export function signedIn(token: string) {
  set(token, 'signed-in')
}

export function signedOut() {
  set(null, 'signed-out')
}

let inflight: Promise<boolean> | null = null

/**
 * Trades the refresh cookie for a new access token. Exactly one refresh runs at a time in this tab, and the
 * Web Lock makes tabs take turns: the backend rotates the cookie, so two tabs presenting the same one at once
 * would look like token reuse and revoke the whole session.
 */
export function refresh(): Promise<boolean> {
  const started = epoch
  // A sign-in or sign-out that happened meanwhile is newer than this result, so it is dropped.
  const apply = (token: string | null) => {
    if (epoch !== started) return accessToken !== null
    if (token) signedIn(token)
    else signedOut()
    return token !== null
  }
  inflight ??= withRefreshLock(async () => {
    try {
      const res = await fetch('/api/auth/refresh-token', {
        method: 'POST', credentials: 'same-origin',
        // the Web Lock is held meanwhile, so a hung request must not block every tab
        signal: AbortSignal.timeout(15_000),
      })
      if (!res.ok) return apply(null)
      const body = (await res.json()) as { data?: { token?: string } }
      return apply(body.data?.token ?? null)
    } catch {
      // shortcut: a network error also signs out; upgrade to a retry banner with the "service unavailable" state.
      return apply(null)
    }
  }).finally(() => { inflight = null })
  return inflight
}

async function withRefreshLock<T>(fn: () => Promise<T>): Promise<T> {
  return typeof navigator !== 'undefined' && navigator.locks
    ? await navigator.locks.request('paygenix-refresh', fn)
    : fn()
}
