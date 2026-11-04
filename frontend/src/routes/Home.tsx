import { t } from '../i18n'

// shortcut: placeholder until the dashboard screen (cards + stories) is built.
export default function Home() {
  return (
    <main className="page">
      <h1>{t('home.title')}</h1>
      <p className="muted">{t('home.body')}</p>
    </main>
  )
}
