import type { ReactElement } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useAuth } from './lib/auth';
import Layout, { NAV } from './components/Layout';
import { Empty, Icon, Loading } from './components/ui';
import type { Permission } from './api/types';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import ClientsPage from './pages/ClientsPage';
import ClientDetailPage from './pages/ClientDetailPage';
import NewCreditPage from './pages/NewCreditPage';
import ApplicationsPage from './pages/ApplicationsPage';
import ContractsPage from './pages/ContractsPage';
import ContractDetailPage from './pages/ContractDetailPage';
import PaymentsPage from './pages/PaymentsPage';
import CollectionPage from './pages/CollectionPage';
import ProductsPage from './pages/ProductsPage';
import WarehousePage from './pages/WarehousePage';
import NotificationsPage from './pages/NotificationsPage';
import SettingsPage from './pages/SettingsPage';
import KpiPage from './pages/KpiPage';
import AnalyticsPage from './pages/AnalyticsPage';
import ForecastPage from './pages/ForecastPage';
import BranchesPage from './pages/BranchesPage';

/** Ruxsat bo'lmasa sahifa o'rniga tushuntirish chiqadi (backend ham baribir 403 qaytaradi). */
function Guard({ perms, children }: { perms: Permission[]; children: ReactElement }) {
  const { can } = useAuth();
  const { t } = useTranslation();
  if (can(...perms)) return children;
  return (
    <div className="page">
      <Empty>
        <div className="col center" style={{ gap: 10 }}>
          <Icon name="lock" size={28} />
          <b>{t('noAccess')}</b>
          <span className="faint">{t('noAccessHint')}</span>
        </div>
      </Empty>
    </div>
  );
}

/** Bosh sahifaga ruxsat bo'lmasa — birinchi ruxsat berilgan bo'limga o'tadi. */
function Home() {
  const { can } = useAuth();
  if (can('DASHBOARD_VIEW')) return <DashboardPage />;
  const first = NAV.flatMap((g) => g.items).find((i) => i.to !== '/' && (!i.perms || can(...i.perms)));
  return <Navigate to={first?.to ?? '/kpi'} replace />;
}

export default function App() {
  const { user, ready } = useAuth();
  if (!ready) return <Loading />;
  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<LoginPage />} />
      </Routes>
    );
  }
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="clients" element={<Guard perms={['CLIENTS_VIEW']}><ClientsPage /></Guard>} />
        <Route path="clients/:id" element={<Guard perms={['CLIENTS_VIEW']}><ClientDetailPage /></Guard>} />
        <Route path="new-credit" element={<Guard perms={['CONTRACTS_CREATE']}><NewCreditPage /></Guard>} />
        <Route path="applications" element={<Guard perms={['CONTRACTS_VIEW']}><ApplicationsPage /></Guard>} />
        <Route path="contracts" element={<Guard perms={['CONTRACTS_VIEW']}><ContractsPage /></Guard>} />
        <Route path="contracts/:id" element={<Guard perms={['CONTRACTS_VIEW']}><ContractDetailPage /></Guard>} />
        <Route path="payments" element={<Guard perms={['PAYMENTS_VIEW']}><PaymentsPage /></Guard>} />
        <Route path="collection/:stage?" element={<Guard perms={['COLLECTION_VIEW']}><CollectionPage /></Guard>} />
        <Route path="products" element={<Guard perms={['PRODUCTS_VIEW']}><ProductsPage /></Guard>} />
        <Route path="warehouse" element={<Guard perms={['PRODUCTS_VIEW']}><WarehousePage /></Guard>} />
        <Route path="notifications" element={<Guard perms={['NOTIFICATIONS_VIEW']}><NotificationsPage /></Guard>} />
        <Route path="kpi" element={<KpiPage />} />
        <Route path="analytics" element={<Guard perms={['ANALYTICS_VIEW']}><AnalyticsPage /></Guard>} />
        <Route path="forecast" element={<Guard perms={['FORECAST_VIEW']}><ForecastPage /></Guard>} />
        <Route path="simulator" element={<Guard perms={['FORECAST_VIEW']}><ForecastPage mode="simulator" /></Guard>} />
        <Route path="branches" element={<Guard perms={['BRANCHES_VIEW']}><BranchesPage /></Guard>} />
        <Route path="settings/:tab?" element={<Guard perms={['SETTINGS_EDIT', 'USERS_MANAGE', 'DOCUMENTS_EDIT']}><SettingsPage /></Guard>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
