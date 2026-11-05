/** Accepts "+994 50 123 45 67", "050 123 45 67" or "501234567"; the API wants +994 and 9 digits. */
export function toPhone(s: string): string | null {
  const d = s.replace(/\D/g, '')
  const local = d.length === 12 && d.startsWith('994') ? d.slice(3) : d.length === 10 && d.startsWith('0') ? d.slice(1) : d
  return /^\d{9}$/.test(local) ? `+994${local}` : null
}
