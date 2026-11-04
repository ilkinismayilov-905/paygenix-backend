import { signedOut } from './session'

/** Revokes this browser's session through the refresh cookie (which the server also clears), then forgets the token locally. */
export async function signOut() {
  try {
    // Plain fetch, not the api client: a 401 here must not trigger a refresh.
    await fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin', signal: AbortSignal.timeout(15_000) })
  } catch {
    // shortcut: offline sign-out leaves the cookie, so a reload signs back in; acceptable until a retry banner exists (T-119).
  } finally {
    // Even if the server call fails, this tab must not stay signed in.
    signedOut()
  }
}
