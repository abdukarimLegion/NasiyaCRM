import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Icon } from './ui';
import type { IconName } from './icons';
import { useAuth } from '../lib/auth';
import { setLang } from '../i18n';
import { api } from '../api/client';
import type { Role, Settings } from '../api/types';

interface NavItem { to: string; key: string; icon: IconName; roles?: Role[] }

const NAV: { group: string; items: NavItem[] }[] = [
  { group: 'nav_main', items: [
    { to: '/', key: 'dashboard', icon: 'dashboard' },
    { to: '/clients', key: 'clients', icon: 'clients' },
  ] },
  { group: 'nav_credit', items: [
    { to: '/new-credit', key: 'newCredit', icon: 'newcredit', roles: ['ADMIN', 'CREDIT_OFFICER'] },
    { to: '/contracts', key: 'contracts', icon: 'contracts' },
    { to: '/payments', key: 'payments', icon: 'payments' },
    { to: '/collection', key: 'collection', icon: 'collection', roles: ['ADMIN', 'COLLECTOR', 'CREDIT_OFFICER'] },
  ] },
  { group: 'nav_admin', items: [
    { to: '/products', key: 'products', icon: 'briefcase' },
    { to: '/notifications', key: 'notifications', icon: 'bell' },
    { to: '/settings', key: 'settings', icon: 'settings', roles: ['ADMIN'] },
  ] },
];

function readPref(key: string, def: string): string {
  try {
    return localStorage.getItem(key) ?? def;
  } catch {
    return def;
  }
}

export default function Layout() {
  const { t, i18n } = useTranslation();
  const { user, logout, hasRole } = useAuth();
  const location = useLocation();
  const [dark, setDark] = useState(() => readPref('theme', 'light') === 'dark');
  const [collapsed, setCollapsed] = useState(false);
  // Telefonda yon panel chiqib-kiradigan panel (drawer) bo'ladi
  const [drawer, setDrawer] = useState(false);
  const { data: settings } = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/api/settings') });

  useEffect(() => {
    try {
      localStorage.setItem('theme', dark ? 'dark' : 'light');
    } catch {
      /* ignore */
    }
  }, [dark]);

  useEffect(() => {
    if (settings?.brandName) document.title = settings.brandName;
  }, [settings?.brandName]);

  const current = NAV.flatMap((g) => g.items)
    .filter((i) => (i.to === '/' ? location.pathname === '/' : location.pathname.startsWith(i.to)))[0];
  const lang = i18n.language === 'ru' ? 'ru' : 'uz';

  return (
    <div className="app" data-theme={dark ? 'dark' : 'light'} data-density="regular" data-layout="sidebar"
      data-collapsed={collapsed} data-drawer={drawer}
      style={{ height: '100%', ...(settings?.primaryColor ? { ['--accent-base' as string]: settings.primaryColor } : {}) }}>
      <aside className="sidebar">
        <div className="brand">
          {settings?.logoUrl
            ? <img src={settings.logoUrl} alt="" className="brand-mark" style={{ objectFit: 'contain' }} />
            : <div className="brand-mark">ن</div>}
          <div className="brand-text">
            <b>{settings?.brandName ?? t('appName')}</b>
            <span>CRM</span>
          </div>
        </div>
        <nav className="nav">
          {NAV.map((grp) => {
            const items = grp.items.filter((i) => !i.roles || hasRole(...i.roles));
            if (items.length === 0) return null;
            return (
              <div key={grp.group}>
                <div className="nav-label">{t(grp.group)}</div>
                {items.map((it) => (
                  <NavLink key={it.to} to={it.to} end={it.to === '/'} className="nav-item"
                    onClick={() => setDrawer(false)} style={{ textDecoration: 'none' }}>
                    <span className="nav-ic"><Icon name={it.icon} /></span>
                    <span>{t(it.key)}</span>
                  </NavLink>
                ))}
              </div>
            );
          })}
        </nav>
        <div className="sidebar-foot">
          <div className="user-chip">
            <span className="avatar">{user?.fullName.split(' ').map((p) => p[0]).join('').slice(0, 2)}</span>
            <div className="u-meta">
              <b>{user?.fullName}</b>
              <span>{user && t(`r_${user.role}`)}</span>
            </div>
            <button className="iconbtn" title={t('logout')} onClick={logout}
              style={{ marginLeft: 'auto', width: 32, height: 32 }}>
              <Icon name="chevR" size={16} />
            </button>
          </div>
        </div>
      </aside>

      <button className="drawer-scrim" aria-label={t('close')} onClick={() => setDrawer(false)} />

      <div className="app-main">
        <header className="topbar">
          <button className="iconbtn" style={{ border: 0, background: 'transparent' }}
            onClick={() => {
              if (window.matchMedia('(max-width: 880px)').matches) setDrawer((d) => !d);
              else setCollapsed((c) => !c);
            }}>
            <Icon name="menu" />
          </button>
          <div className="col" style={{ gap: 0 }}>
            <div className="page-title">{t(current?.key ?? 'dashboard')}</div>
            {settings && <div className="page-sub">{settings.companyName}</div>}
          </div>
          <div className="topbar-spacer" />
          <div className="seg" style={{ flex: 'none' }}>
            {(['uz', 'ru'] as const).map((l) => (
              <button key={l} data-on={lang === l} onClick={() => setLang(l)}
                style={{ padding: '7px 11px', textTransform: 'uppercase', fontSize: 12 }}>{l}</button>
            ))}
          </div>
          <button className="iconbtn" onClick={() => setDark((d) => !d)} title="Theme">
            <Icon name={dark ? 'sun' : 'moon'} />
          </button>
        </header>
        <Outlet />
      </div>
    </div>
  );
}
