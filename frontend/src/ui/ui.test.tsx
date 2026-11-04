import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { expect, test } from 'vitest'
import { formatMoney } from './formatMoney'
import { PinInput } from './PinInput'

test('formats decimal strings exactly, with the currency symbol', () => {
  expect(formatMoney('2480.50', 'AZN')).toBe('₼2,480.50')
  expect(formatMoney('-45', 'USD', true)).toBe('-$45.00')
  expect(formatMoney('120.00', 'EUR', true)).toBe('+€120.00')
  // more digits than a double can hold: proves no Number() round-trip
  expect(formatMoney('12345678901234567.89', 'AZN')).toBe('₼12,345,678,901,234,567.89')
})

function Pin() {
  const [v, setV] = useState('')
  return <><PinInput label="PIN" value={v} onChange={setV} /><output>{v}</output></>
}

test('PIN input fills in order, deletes back and takes a paste', () => {
  render(<Pin />)
  const boxes = screen.getAllByLabelText(/Digit/)
  fireEvent.change(boxes[3]!, { target: { value: '7' } }) // out of order: lands in the first empty box
  fireEvent.change(boxes[1]!, { target: { value: '8' } })
  expect(screen.getByRole('status').textContent).toBe('78')
  fireEvent.keyDown(boxes[2]!, { key: 'Backspace' })
  expect(screen.getByRole('status').textContent).toBe('7')
  fireEvent.paste(boxes[0]!, { clipboardData: { getData: () => '12 34-56' } })
  expect(screen.getByRole('status').textContent).toBe('123456')
})
