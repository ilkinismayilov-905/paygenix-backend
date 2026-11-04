import { useState } from 'react'
import { Alert, Avatar, Button, CardTile, Dialog, EmptyState, ErrorState, List, ListRow, Money, PinInput, Skeleton, TextField } from '../ui'
import './design-preview.css'

// Sample data for the owner's design review only; no API calls here.
const cards = [
  { id: 1, maskedNumber: '4169 **** **** 2207', holder: 'Aysel Hüseynova', expires: '09/29', network: 'VISA', currency: 'AZN', balance: '2480.50' },
  { id: 2, maskedNumber: '5355 **** **** 8814', holder: 'Aysel Hüseynova', expires: '03/28', network: 'MASTERCARD', currency: 'USD', balance: '312.07' },
] as const

const activity = [
  { name: 'Rəşad Əliyev', note: 'Transfer · Today, 14:20', amount: '-45.00' },
  { name: 'Grocery store', note: 'Payment · Today, 09:12', amount: '-18.36' },
  { name: 'Leyla Məmmədova', note: 'Transfer · Yesterday', amount: '120.00' },
  { name: 'Mobile top-up', note: 'Payment · 2 Nov', amount: '-10.00' },
]

type Theme = 'system' | 'light' | 'dark'

function setTheme(theme: Theme) {
  if (theme === 'system') delete document.documentElement.dataset.theme
  else document.documentElement.dataset.theme = theme
}

const Icon = {
  send: <path d="M7 17 17 7M9 7h8v8" />,
  pay: <path d="M4 7h16v10H4zM4 11h16" />,
  add: <path d="M12 5v14M5 12h14" />,
}

function Action({ icon, label }: { icon: keyof typeof Icon; label: string }) {
  return (
    <button type="button" className="dp-action">
      <span className="dp-action-icon">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{Icon[icon]}</svg>
      </span>
      {label}
    </button>
  )
}

function SampleHome() {
  const [selected, setSelected] = useState(1)
  return (
    <div className="dp-phone">
      <header className="dp-greeting">
        <div>
          <p className="dp-muted">Good evening</p>
          <h1>Aysel</h1>
        </div>
        <Avatar name="Aysel Hüseynova" />
      </header>

      <div className="dp-cards" role="group" aria-label="Your cards">
        {cards.map((c) => (
          <CardTile key={c.id} {...c} selected={selected === c.id} onSelect={() => setSelected(c.id)} />
        ))}
      </div>

      <nav className="dp-actions" aria-label="Quick actions">
        <Action icon="send" label="Send" />
        <Action icon="pay" label="Pay" />
        <Action icon="add" label="New card" />
      </nav>

      <section className="dp-section" aria-labelledby="recent">
        <div className="dp-section-head">
          <h2 id="recent">Recent activity</h2>
          <Button variant="ghost" size="sm">See all</Button>
        </div>
        <List>
          {activity.map((a) => (
            <ListRow key={a.name} leading={<Avatar name={a.name} />} title={a.name} subtitle={a.note}
              trailing={<Money amount={a.amount} currency="AZN" signed />} />
          ))}
        </List>
      </section>
    </div>
  )
}

function Gallery() {
  const [pin, setPin] = useState('')
  const [open, setOpen] = useState(false)
  return (
    <div className="dp-gallery">
      <section className="dp-block">
        <h2>Colour</h2>
        <div className="dp-swatches">
          {['bg', 'surface', 'surface-sunken', 'border', 'text', 'text-muted', 'primary', 'accent', 'success', 'danger', 'warning'].map((t) => (
            <figure key={t} className="dp-swatch">
              <span style={{ background: `var(--${t})` }} />
              <figcaption>{t}</figcaption>
            </figure>
          ))}
        </div>
      </section>

      <section className="dp-block">
        <h2>Type</h2>
        <Money amount="12840.75" currency="AZN" size="xl" />
        <h1>Send money to Rəşad</h1>
        <h2>Recent activity</h2>
        <h3>Card details</h3>
        <p>Body text. Ödəniş təsdiqləndi — şəxsi, ğ, ç, ö, ü, ı, ə.</p>
        <p className="dp-muted">Muted text for hints and metadata.</p>
        <div className="dp-amounts">
          <Money amount="1.10" currency="AZN" /><Money amount="1111.11" currency="AZN" /><Money amount="88.08" currency="EUR" /><Money amount="-45.00" currency="USD" signed />
        </div>
      </section>

      <section className="dp-block">
        <h2>Buttons</h2>
        <div className="dp-row">
          <Button>Send money</Button>
          <Button variant="secondary">Cancel</Button>
          <Button variant="ghost">See all</Button>
          <Button variant="danger">Block card</Button>
          <Button loading>Sending</Button>
          <Button disabled>Disabled</Button>
          <Button size="sm">Small</Button>
        </div>
      </section>

      <section className="dp-block">
        <h2>Inputs</h2>
        <div className="dp-form">
          <TextField label="Email" type="email" placeholder="you@example.com" hint="We send a code to this address." />
          <TextField label="Amount" inputMode="decimal" placeholder="0.00" suffix="₼" />
          <TextField label="Username" defaultValue="aysel" error="This username is taken." />
          <PinInput label="Enter your PIN" value={pin} onChange={setPin} />
          <PinInput label="PIN with an error" value="1234" onChange={() => {}} error="Wrong PIN. 3 attempts left." />
        </div>
      </section>

      <section className="dp-block">
        <h2>Alerts</h2>
        <div className="dp-stack">
          <Alert title="Code sent">Check your inbox for a 6-digit code.</Alert>
          <Alert tone="success" title="Money sent">45.00 ₼ is on its way to Rəşad.</Alert>
          <Alert tone="warning" title="One attempt left">After one more wrong PIN, payments lock for 15 minutes.</Alert>
          <Alert tone="danger" title="Not enough money on this card">Choose another card or send a smaller amount.</Alert>
        </div>
      </section>

      <section className="dp-block">
        <h2>Dialog</h2>
        <div className="dp-row"><Button variant="secondary" onClick={() => setOpen(true)}>Block this card</Button></div>
        <Dialog open={open} onClose={() => setOpen(false)} title="Block card ending 2207?"
          actions={<><Button variant="secondary" onClick={() => setOpen(false)}>Keep card</Button><Button variant="danger" onClick={() => setOpen(false)}>Block card</Button></>}>
          Payments and transfers from this card stop right away. You can order a new card later.
        </Dialog>
      </section>

      <section className="dp-block">
        <h2>Loading, empty and error</h2>
        <div className="dp-states">
          <div className="dp-panel">
            <div className="dp-stack">
              <Skeleton height="1.25rem" width="40%" />
              <Skeleton height="2.5rem" width="70%" />
              <Skeleton height="1rem" />
              <Skeleton height="1rem" width="85%" />
            </div>
          </div>
          <div className="dp-panel"><EmptyState title="No transfers yet" body="Money you send and receive shows up here." action={<Button size="sm">Send money</Button>} /></div>
          <div className="dp-panel"><ErrorState title="Couldn't load your cards" body="Check your connection and try again." action={<Button size="sm" variant="secondary">Try again</Button>} /></div>
        </div>
      </section>
    </div>
  )
}

export default function DesignPreview() {
  const [theme, setThemeState] = useState<Theme>('system')
  const choose = (t: Theme) => { setTheme(t); setThemeState(t) }
  return (
    <div className="dp">
      <div className="dp-toolbar" role="radiogroup" aria-label="Theme">
        {(['system', 'light', 'dark'] as const).map((t) => (
          <button key={t} type="button" role="radio" aria-checked={theme === t} className="dp-theme" onClick={() => choose(t)}>
            {t[0]!.toUpperCase() + t.slice(1)}
          </button>
        ))}
      </div>
      <div className="dp-layout">
        <SampleHome />
        <Gallery />
      </div>
    </div>
  )
}
