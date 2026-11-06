import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { components } from '../api/schema'

export type LegalType = 'terms' | 'privacy'
export type LegalRef = components['schemas']['LegalDocumentRef']
type Doc = { type: string; version: string; content: string }
/** A loaded document, 'missing' when none is published (404), 'failed' when it could not be loaded. */
export type LegalDoc = Doc | 'missing' | 'failed'

export const legalTypeOf = (type: string): LegalType => (type === 'PRIVACY' ? 'privacy' : 'terms')

async function fetchLegal(type: LegalType): Promise<LegalDoc> {
  try {
    const { data, response } = await api.GET(type === 'terms' ? '/api/v1/legal/terms' : '/api/v1/legal/privacy')
    const doc = data?.data
    if (response.ok && doc?.type && doc.version) return { type: doc.type, version: doc.version, content: doc.content ?? '' }
    return response.status === 404 ? 'missing' : 'failed'
  } catch {
    return 'failed'
  }
}

/** The active Terms and Privacy, loaded once; `reload` fetches them again (e.g. after a new version appeared). */
export function useLegalDocs() {
  const [docs, setDocs] = useState<Partial<Record<LegalType, LegalDoc>>>({})
  const [round, setRound] = useState(0)
  useEffect(() => {
    let live = true
    for (const type of ['terms', 'privacy'] as const) {
      void fetchLegal(type).then((doc) => { if (live) setDocs((d) => ({ ...d, [type]: doc })) })
    }
    return () => { live = false }
  }, [round])
  return { docs, reload: () => { setDocs({}); setRound((r) => r + 1) } }
}

/** The versions the user was shown, or null while one is still loading or failed to load. */
export function shownVersions(docs: Partial<Record<LegalType, LegalDoc>>): LegalRef[] | null {
  const all = [docs.terms, docs.privacy]
  if (all.some((d) => d === undefined || d === 'failed')) return null
  return all.filter((d): d is Doc => typeof d === 'object').map(({ type, version }) => ({ type, version }))
}

export type Me = { pending: LegalRef[]; admin: boolean }

/** What the signed-in user still has to accept, and whether they are an admin; null when it could not be checked. */
export async function loadMe(): Promise<Me | null> {
  try {
    const { data, response } = await api.GET('/api/v1/auth/me')
    if (!response.ok) return null
    return { pending: data?.data?.pendingLegalDocuments ?? [], admin: data?.data?.roles?.includes('ADMIN_ROLE') ?? false }
  } catch {
    return null
  }
}

export const loadPendingLegal = async (): Promise<LegalRef[] | null> => (await loadMe())?.pending ?? null
