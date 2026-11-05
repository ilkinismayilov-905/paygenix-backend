import { useSyncExternalStore } from 'react'

/**
 * Who is signed in, kept in memory only (ADR 0001): the access token never touches storage,
 * and the refresh token lives in an httpOnly cookie the page cannot read.
 */
export type AuthStatus = 'unknown' | 'signed-in' | 'signed-out' | 'unavailable'

let accessToken: string | null = null
let status: AuthStatus = 'unknown'
// True only when a live session ended on its own (refresh rejected), so sign-in can say "your session ended".
let expired = false
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

export const sessionExpired = () => expired

export function signedOut() {
  expired = false
  set(null, 'signed-out')
}

// Sign-out in one tab signs out the others; the server already revoked the shared cookie.
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('paygenix-auth') : null
if (channel) channel.onmessage = (e) => { if (e.data === 'signed-out' && status !== 'signed-out') signedOut() }
export const broadcastSignOut = () => channel?.postMessage('signed-out')

let inflight: Promise<boolean> | null = null

/**
 * Trades the refresh cookie for a new access token. Exactly one refresh runs at a time in this tab, and the
 * Web Lock makes tabs take turns: the backend rotates the cookie, so two tabs presenting the same one at once
 * would look like token reuse and revoke the whole session.
 */
export function refresh(): Promise<boolean> {
  const started = epoch
  // A sign-in or sign-out that happened meanwhile is newer than this result, so it is dropped.
  // null token: the server said no (signed out); 'unavailable': it could not be reached, so keep no token but don't sign out.
  const apply = (token: string | null | 'unavailable') => {
    if (epoch !== started) return accessToken !== null
    if (token === 'unavailable') set(null, 'unavailable')
    else if (token) signedIn(token)
    else {
      const wasSignedIn = status === 'signed-in'
      signedOut()
      expired = wasSignedIn
    }
    return typeof token === 'string' && token !== 'unavailable'
  }
  inflight ??= withRefreshLock(async () => {
    try {
      const res = await fetch('/api/auth/refresh-token', {
        method: 'POST', credentials: 'same-origin',
        // the Web Lock is held meanwhile, so a hung request must not block every tab
        signal: AbortSignal.timeout(15_000),
      })
      if (res.status >= 500) return apply('unavailable')
      if (!res.ok) return apply(null)
      const body = (await res.json()) as { data?: { token?: string } }
      return apply(body.data?.token ?? null)
    } catch {
      return apply('unavailable') // offline or timed out
    }
  }).finally(() => { inflight = null })
  return inflight
}

async function withRefreshLock<T>(fn: () => Promise<T>): Promise<T> {
  return typeof navigator !== 'undefined' && navigator.locks
    ? await navigator.locks.request('paygenix-refresh', fn)
    : fn()
}
