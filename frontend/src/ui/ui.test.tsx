import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { expect, test, vi } from 'vitest'
import { Alert } from './Alert'
import { Dialog } from './Dialog'
import { formatMoney } from './formatMoney'
import { PinInput } from './PinInput'
import { TextField } from './TextField'

test('formats decimal strings exactly, with the currency symbol', () => {
  expect(formatMoney('2480.50', 'AZN')).toBe('₼2,480.50')
  expect(formatMoney('-45', 'USD', true)).toBe('-$45.00')
  expect(formatMoney('120.00', 'EUR', true)).toBe('+€120.00')
  // more digits than a double can hold: proves no Number() round-trip
  expect(formatMoney('12345678901234567.89', 'AZN')).toBe('₼12,345,678,901,234,567.89')
})

test('rejects blank or pre-formatted amounts instead of showing 0 or NaN', () => {
  expect(() => formatMoney('', 'AZN')).toThrow()
  expect(() => formatMoney('$1,300.00', 'USD')).toThrow()
})

test('text field error replaces the hint and is announced with the input', () => {
  render(<TextField label="Username" hint="Pick one" error="This username is taken." />)
  const input = screen.getByLabelText('Username')
  expect(input).toHaveAttribute('aria-invalid', 'true')
  expect(input).toHaveAccessibleDescription('This username is taken.')
  expect(screen.queryByText('Pick one')).toBeNull()
})

test('danger alerts interrupt, other alerts are polite', () => {
  render(<><Alert tone="danger" title="Failed" /><Alert tone="success" title="Sent" /></>)
  expect(screen.getByRole('alert')).toHaveTextContent('Failed')
  expect(screen.getByRole('status')).toHaveTextContent('Sent')
})

test('dialog follows the open prop and reports a close once', () => {
  // jsdom has no <dialog> behaviour; mimic the browser
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open'); this.dispatchEvent(new Event('close')) }
  const onClose = vi.fn()
  const { rerender } = render(<Dialog open title="Block card?" onClose={onClose}>Body</Dialog>)
  const dialog = screen.getByRole('dialog', { hidden: true })
  expect(dialog).toHaveAttribute('open')
  expect(dialog).toHaveAccessibleName('Block card?')
  fireEvent.click(screen.getByText('Body')) // inside the box: stays open
  expect(dialog).toHaveAttribute('open')
  fireEvent.click(dialog) // backdrop
  expect(onClose).toHaveBeenCalledTimes(1)
  rerender(<Dialog open={false} title="Block card?" onClose={onClose}>Body</Dialog>)
  expect(onClose).toHaveBeenCalledTimes(1)
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
  fireEvent.keyDown(boxes[0]!, { key: 'Backspace' }) // first box with a digit: the browser clears it
  fireEvent.paste(boxes[0]!, { clipboardData: { getData: () => 'no digits' } })
  expect(screen.getByRole('status').textContent).toBe('7')
  fireEvent.paste(boxes[0]!, { clipboardData: { getData: () => '12 34-56' } })
  expect(screen.getByRole('status').textContent).toBe('123456')
})
