import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Icon } from './ui';
import type { IconName } from './icons';
import { useAuth } from '../lib/auth';
import { setLang } from '../i18n';
import { api } from '../api/client';
import type { Permission, Settings } from '../api/types';
import { useBranch } from '../lib/branch';

interface NavItem { to: string; key: string; icon: IconName; perms?: Permission[] }

// eslint-disable-next-line react-refresh/only-export-components
export const NAV: { group: string; items: NavItem[] }[] = [
  { group: 'nav_main', items: [
    { to: '/', key: 'dashboard', icon: 'dashboard', perms: ['DASHBOARD_VIEW'] },
  ] },
  { group: 'nav_front', items: [
    { to: '/new-credit', key: 'newCredit', icon: 'newcredit', perms: ['CONTRACTS_CREATE'] },
    { to: '/applications', key: 'applications', icon: 'doc', perms: ['CONTRACTS_VIEW'] },
    { to: '/clients', key: 'clients', icon: 'clients', perms: ['CLIENTS_VIEW'] },
    { to: '/payments', key: 'cashierNav', icon: 'payments', perms: ['PAYMENTS_VIEW'] },
    { to: '/products', key: 'products', icon: 'briefcase', perms: ['PRODUCTS_VIEW'] },
    { to: '/contracts', key: 'contracts', icon: 'contracts', perms: ['CONTRACTS_VIEW'] },
  ] },
  { group: 'nav_collection', items: [
    { to: '/collection', key: 'collection', icon: 'collection', perms: ['COLLECTION_VIEW'] },
    { to: '/notifications', key: 'notifications', icon: 'bell', perms: ['NOTIFICATIONS_VIEW'] },
  ] },
  { group: 'nav_analytics', items: [
    { to: '/kpi', key: 'kpiNav', icon: 'target' },
    { to: '/analytics', key: 'analyticsNav', icon: 'analytics', perms: ['ANALYTICS_VIEW'] },
    { to: '/forecast', key: 'forecastNav', icon: 'trend', perms: ['FORECAST_VIEW'] },
  ] },
  { group: 'nav_system', items: [
    { to: '/branches', key: 'branchesNav', icon: 'building', perms: ['BRANCHES_VIEW'] },
    { to: '/settings', key: 'settings', icon: 'settings', perms: ['SETTINGS_EDIT', 'USERS_MANAGE', 'DOCUMENTS_EDIT'] },
  ] },
];

const THEMES = ['light-emerald', 'light-blue', 'dark-slate'] as const;
type Theme = (typeof THEMES)[number];
const THEME_SWATCH: Record<Theme, string> = { 'light-emerald': '#059669', 'light-blue': '#2563eb', 'dark-slate': '#0f172a' };

/** Saqlangan mavzu; eski "light"/"dark" qiymatlari yangi nomlarga o'tkaziladi */
function readTheme(): Theme {
  try {
    const v = localStorage.getItem('theme');
    if (v === 'dark') return 'dark-slate';
    return THEMES.find((x) => x === v) ?? 'light-emerald';
  } catch {
    return 'light-emerald';
  }
}

export default function Layout() {
  const { t, i18n } = useTranslation();
  const { user, logout, can } = useAuth();
  const branch = useBranch();
  const location = useLocation();
  const [theme, setTheme] = useState<Theme>(readTheme);
  const [collapsed, setCollapsed] = useState(false);
  // Telefonda yon panel chiqib-kiradigan panel (drawer) bo'ladi
  const [drawer, setDrawer] = useState(false);
  const { data: settings } = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/api/settings') });

  useEffect(() => {
    try {
      localStorage.setItem('theme', theme);
    } catch {
      /* ignore */
    }
  }, [theme]);

  useEffect(() => {
    if (settings?.brandName) document.title = settings.brandName;
  }, [settings?.brandName]);

  const isCurrent = (i: NavItem) => (i.to === '/' ? location.pathname === '/' : location.pathname.startsWith(i.to));
  const currentGroup = NAV.find((g) => g.items.some(isCurrent));
  const current = currentGroup?.items.find(isCurrent);
  const lang = i18n.language === 'ru' ? 'ru' : 'uz';

  return (
    <div className="app" data-theme={theme} data-density="regular" data-layout="sidebar"
      data-collapsed={collapsed} data-drawer={drawer}
      style={{ height: '100%', ...(settings?.primaryColor ? { ['--accent-base' as string]: settings.primaryColor } : {}) }}>
      <aside className="sidebar">
        <div className="brand">
          {settings?.logoUrl
            ? <img src={settings.logoUrl} alt="" className="brand-mark" style={{ objectFit: 'contain' }} />
            : <div className="brand-mark"><Icon name="trend" /></div>}
          <div className="brand-text">
            <b>{settings?.brandName ?? t('appName')}</b>
            <span>{t('brandTag')}</span>
          </div>
        </div>
        <nav className="nav">
          {NAV.map((grp) => {
            const items = grp.items.filter((i) => !i.perms || can(...i.perms));
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
              <span>{user && t(`r_${user.role}`)}{user?.branchName ? ` · ${user.branchName}` : ''}</span>
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
          <div className="crumb">
            <span className="crumb-cat">{t(currentGroup?.group ?? 'nav_main')}</span>
            <span className="crumb-sep"><Icon name="chevR" size={13} /></span>
            <span className="crumb-page">{t(current?.key ?? 'dashboard')}</span>
          </div>
          <div className="topbar-spacer" />
          {branch.canSwitch && branch.branches.length > 1 && (
            <label className="pill-select" title={t('branch')}>
              <Icon name="building" size={15} />
              <select value={branch.selected ?? ''} aria-label={t('branch')}
                onChange={(e) => branch.setSelected(e.target.value ? Number(e.target.value) : null)}>
                <option value="">{t('allBranches')}</option>
                {branch.branches.filter((b) => b.active).map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
              </select>
            </label>
          )}
          <div className="seg" style={{ flex: 'none' }}>
            {(['uz', 'ru'] as const).map((l) => (
              <button key={l} data-on={lang === l} onClick={() => setLang(l)}
                style={{ textTransform: 'uppercase', fontFamily: 'var(--font-mono)', fontSize: 11 }}>{l}</button>
            ))}
          </div>
          <button className="pill-btn" title={t('themeSwitch')}
            onClick={() => setTheme((cur) => THEMES[(THEMES.indexOf(cur) + 1) % THEMES.length])}>
            <span className="swatch" style={{ background: THEME_SWATCH[theme] }} />
            <span className="hide-sm">{t(`th_${theme}`)}</span>
          </button>
        </header>
        <Outlet />
      </div>
    </div>
  );
}
