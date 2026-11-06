import { expect, test } from 'vitest'
import { addAmounts, percentOf } from './fees'

test('percentOf rounds half-even to cents like the server', () => {
  expect(percentOf('10.00', '2')).toBe('0.20')
  expect(percentOf('0.25', '2')).toBe('0.00') // 0.005 → 0.00 (even)
  expect(percentOf('0.75', '2')).toBe('0.02') // 0.015 → 0.02 (even)
  expect(percentOf('1.25', '2')).toBe('0.02') // 0.025 → 0.02
  expect(percentOf('10.00', '0.5')).toBe('0.05')
  expect(percentOf('200000.00', '0.5')).toBe('1000.00')
  expect(percentOf('33.33', '2.25')).toBe('0.75') // 0.749925
  expect(percentOf('999999999999999.99', '2')).toBe('20000000000000.00')
})

test('addAmounts is exact', () => {
  expect(addAmounts('10.00', '0.20')).toBe('10.20')
  expect(addAmounts('0.10', '0.20')).toBe('0.30')
})
