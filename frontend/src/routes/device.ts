import { t } from '../i18n'

// Order matters: Edge and Opera say "Chrome" too, every Chromium says "Safari", iPadOS may say "Macintosh".
const BROWSERS: [RegExp, string][] = [
  [/Edg(e|A|iOS)?\//, 'Edge'], [/OPR\/|Opera/, 'Opera'], [/SamsungBrowser\//, 'Samsung Internet'],
  [/Firefox\/|FxiOS\//, 'Firefox'], [/Chrome\/|CriOS\//, 'Chrome'], [/Safari\//, 'Safari'],
]
const SYSTEMS: [RegExp, string][] = [
  [/iPhone/, 'iPhone'], [/iPad/, 'iPad'], [/Android/, 'Android'], [/Windows/, 'Windows'],
  [/Mac OS X|Macintosh/, 'macOS'], [/CrOS/, 'ChromeOS'], [/Linux/, 'Linux'],
]

/** "Chrome on Windows" from the User-Agent the session was started with; the raw string when nothing matches. */
export function deviceLabel(userAgent?: string | null): string {
  const ua = userAgent?.trim()
  if (!ua) return t('sessions.unknownDevice')
  const browser = BROWSERS.find(([re]) => re.test(ua))?.[1]
  const system = SYSTEMS.find(([re]) => re.test(ua))?.[1]
  if (browser && system) return t('sessions.device', { browser, system })
  return browser ?? system ?? (ua.length > 60 ? `${ua.slice(0, 60)}…` : ua)
}
