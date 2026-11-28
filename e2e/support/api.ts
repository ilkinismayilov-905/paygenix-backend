import { request, type APIRequestContext, type APIResponse } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { env } from './env'
import { codeSentTo } from './mailpit'

export const PASSWORD = 'e2e-correct-horse-battery'
export const PIN = '246810'

export type TestUser = { email: string; username: string; fullName: string; password: string; pin: string }

/** A fresh, unique identity (the stack's database is shared between runs). */
export function newIdentity(prefix = 'e2e'): TestUser {
  const id = randomUUID().replaceAll('-', '').slice(0, 10)
  return {
    email: `${prefix}-${id}@example.test`,
    username: `${prefix}_${id}`,
    fullName: `E2E ${prefix} ${id}`,
    password: PASSWORD,
    pin: PIN,
  }
}

// `any` on purpose: each caller knows the shape of the endpoint it called
async function ok(res: APIResponse): Promise<any> {
  if (!res.ok()) throw new Error(`${res.url()} → ${res.status()} ${await res.text()}`)
  return ((await res.json()) as { data: unknown }).data
}

/** Signs a user up through the same API calls as the sign-up screen, reading the code from Mailpit. */
export async function signUpViaApi(prefix = 'e2e'): Promise<TestUser> {
  const user = newIdentity(prefix)
  const api = await request.newContext({ baseURL: env.baseURL })
  try {
    const since = new Date()
    const flowId: string = await ok(await api.post('/api/v1/onboarding/email', { data: { email: user.email } }))
    const otp = await codeSentTo(user.email, since)
    const token: string = await ok(await api.post(`/api/v1/onboarding/${flowId}/otp`, { data: { otp } }))
    const auth = { Authorization: `Bearer ${token}` }
    await ok(await api.put(`/api/v1/onboarding/${flowId}/full-name`, { headers: auth, data: { fullName: user.fullName } }))
    await ok(await api.put(`/api/v1/onboarding/${flowId}/username`, { headers: auth, data: { username: user.username } }))
    // the versions the sign-up screen shows and the user accepts
    const legal = await Promise.all(['terms', 'privacy'].map(async (doc) => {
      const { type, version } = await ok(await api.get(`/api/v1/legal/${doc}`))
      return { type, version }
    }))
    await ok(await api.put(`/api/v1/onboarding/${flowId}/pin`, {
      headers: auth,
      data: { pin: user.pin, password: user.password, acceptedLegalDocuments: legal },
    }))
    return user
  } finally {
    await api.dispose()
  }
}

/** An API client signed in as `user` (Bearer token), for setup steps that are not under test. */
export async function apiAs(user: TestUser): Promise<{ api: APIRequestContext; token: string }> {
  const api = await request.newContext({ baseURL: env.baseURL })
  const token: string = (await ok(await api.post('/api/v1/auth/login', {
    data: { email: user.email, password: user.password },
  }))).token
  return { api, token }
}

/** Issues a card through the API and returns its full number (only ever shown in this response). */
export async function issueCard(user: TestUser, currency = 'AZN'): Promise<string> {
  const { api, token } = await apiAs(user)
  try {
    const card = await ok(await api.post('/api/v1/cards', {
      headers: { Authorization: `Bearer ${token}`, 'Idempotency-Key': randomUUID() },
      data: { currency },
    }))
    return String(card.cardNumber)
  } finally {
    await api.dispose()
  }
}

/** The user's open cards as the API reports them (amounts are decimal strings). */
export async function cardsOf(user: TestUser): Promise<{ last4: string; amount: string; currency: string }[]> {
  const { api, token } = await apiAs(user)
  try {
    return await ok(await api.get('/api/v1/cards/me', { headers: { Authorization: `Bearer ${token}` } }))
  } finally {
    await api.dispose()
  }
}
