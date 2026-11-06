import type { components } from '../api/schema'

export type Review = components['schemas']['KycReviewItem']

const when = new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' })
export const formatWhen = (iso?: string) => (iso && !Number.isNaN(Date.parse(iso)) ? when.format(new Date(iso)) : '')
