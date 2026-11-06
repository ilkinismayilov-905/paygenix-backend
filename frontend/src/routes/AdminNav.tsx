import { NavLink } from 'react-router'
import { t } from '../i18n'

/** Tabs between the admin screens. */
export function AdminNav() {
  const links = [['/admin/kyc', 'admin.nav.kyc'], ['/admin/stories', 'promo.title'], ['/admin/reports', 'admin.nav.reports'],
    ['/admin/users', 'admin.nav.users']] as const
  return (
    <nav className="tabs" aria-label={t('admin.nav')}>
      {links.map(([to, label]) => (
        <NavLink key={to} to={to} end={to !== '/admin/kyc'}
          className={({ isActive }) => `btn btn-sm ${isActive ? 'btn-primary' : 'btn-secondary'}`}>{t(label)}</NavLink>
      ))}
    </nav>
  )
}
