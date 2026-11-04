/** The Paygenix mark: P on Caspian blue with the saffron arc of the card tile. */
export function Logo({ size = 28 }: { size?: number }) {
  return (
    <svg viewBox="0 0 32 32" width={size} height={size} aria-hidden="true">
      <rect width="32" height="32" rx="8" fill="var(--caspian-700)" />
      <circle cx="24" cy="8" r="9" fill="none" stroke="var(--saffron-500)" strokeWidth="4" />
      <path d="M11 24V9h6a4.5 4.5 0 0 1 0 9h-6" fill="none" stroke="#fff" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  )
}
