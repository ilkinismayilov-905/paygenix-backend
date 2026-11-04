import { Link } from 'react-router'
import { t } from '../i18n'

export default function NotFound() {
  return (
    <main>
      <h1>{t('notFound.title')}</h1>
      <Link to="/">{t('notFound.back')}</Link>
    </main>
  )
}
