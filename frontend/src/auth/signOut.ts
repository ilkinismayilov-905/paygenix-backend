import { api } from '../api/client'
import { signedOut } from './session'

/** Revokes this device's session (which also clears the refresh cookie), then forgets the token locally. */
export async function signOut() {
  try {
    const { data } = await api.GET('/api/auth/sessions')
    const current = data?.data?.find((s) => s.current)
    if (current?.id) await api.DELETE('/api/auth/sessions/{id}', { params: { path: { id: current.id } } })
  } catch {
    // shortcut: if the server call fails the cookie survives and a reload signs back in; T-118 adds a cookie-based logout.
  } finally {
    // Even if the server call fails, this tab must not stay signed in.
    signedOut()
  }
}
