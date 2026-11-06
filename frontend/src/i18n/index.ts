import { az } from './az'
import { en } from './en'

export type MessageKey = keyof typeof en
export type Locale = 'en' | 'az'

const MESSAGES: Record<Locale, Record<MessageKey, string>> = { en, az }
const STORAGE_KEY = 'paygenix-locale'

/** The saved choice, else the browser's language (Azerbaijani when it starts with "az"), else English. */
function initialLocale(): Locale {
  try {
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved === 'en' || saved === 'az') return saved
  } catch {
    // storage blocked (private mode): fall back to the browser language
  }
  return typeof navigator !== 'undefined' && navigator.language?.toLowerCase().startsWith('az') ? 'az' : 'en'
}

let locale: Locale = initialLocale()
if (typeof document !== 'undefined') document.documentElement.lang = locale

export const getLocale = () => locale

/**
 * Saves the choice and reloads: form schemas and labels read their texts once, at module load, so a reload is the
 * one way every screen switches together. The session survives (refresh cookie).
 */
export function setLocale(next: Locale) {
  try {
    localStorage.setItem(STORAGE_KEY, next)
  } catch {
    // not saved: this tab still switches
  }
  locale = next
  window.location.reload()
}

export function t(key: MessageKey, vars?: Record<string, string | number>): string {
  return MESSAGES[locale][key].replace(/\{(\w+)\}/g, (_, name: string) => String(vars?.[name] ?? `{${name}}`))
}
