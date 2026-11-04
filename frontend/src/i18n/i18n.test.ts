import { expect, test } from 'vitest'
import { t } from '.'

test('looks up English messages', () => {
  expect(t('app.name')).toBe('Paygenix')
})
