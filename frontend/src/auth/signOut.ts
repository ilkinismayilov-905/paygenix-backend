import { broadcastSignOut, signedOut } from './session'

/**
 * Revokes this browser's session through the refresh cookie (which the server also clears), then signs out
 * this tab and the others. Returns false when the server could not be reached: the cookie still works, so the
 * tab stays signed in and the caller asks the user to try again instead of pretending it signed out.
 */
export async function signOut(): Promise<boolean> {
  try {
    // Plain fetch, not the api client: a 401 here must not trigger a refresh.
    const res = await fetch('/api/v1/auth/logout', { method: 'POST', credentials: 'same-origin', signal: AbortSignal.timeout(15_000) })
    if (!res.ok) return false
  } catch {
    return false
  }
  signedOut()
  broadcastSignOut()
  return true
}
