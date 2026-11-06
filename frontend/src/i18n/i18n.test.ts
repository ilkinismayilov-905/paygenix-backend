import { expect, test } from 'vitest'
import { t } from '.'

test('looks up English messages', () => {
  expect(t('app.name')).toBe('Paygenix')
})

test('Azerbaijani has every key with the same placeholders', async () => {
  const { en } = await import('./en')
  const { az } = await import('./az')
  const holes = (s: string) => [...s.matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',')
  expect(Object.keys(az)).toEqual(Object.keys(en))
  for (const key of Object.keys(en) as (keyof typeof en)[]) expect(holes(az[key]), key).toBe(holes(en[key]))
})
