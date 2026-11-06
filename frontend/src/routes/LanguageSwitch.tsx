import { getLocale, setLocale, type Locale } from '../i18n'

const NAMES: Record<Locale, string> = { en: 'English', az: 'Azərbaycanca' } // each in its own language, so anyone finds theirs

/** Two links-as-buttons; the current language is marked and not clickable. */
export function LanguageSwitch() {
  const current = getLocale()
  return (
    <p className="language-switch">
      {(Object.keys(NAMES) as Locale[]).map((l, i) => (
        <span key={l}>
          {i > 0 && ' · '}
          {l === current
            ? <strong aria-current="true" lang={l}>{NAMES[l]}</strong>
            : <button type="button" className="link" lang={l} onClick={() => setLocale(l)}>{NAMES[l]}</button>}
        </span>
      ))}
    </p>
  )
}
