import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { ContractListItem, Dashboard, OverdueRow, Page } from '../api/types';
import {
  Avatar, BarsChart, Btn, Card, CardHead, ContractStatusBadge, Donut, Empty, ErrorBox, Loading, MiniStat, Stat,
} from '../components/ui';
import { fmtMln, fmtDate } from '../lib/format';

export default function DashboardPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();

  const { data, isLoading, error } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api.get<Dashboard>('/api/dashboard'),
  });
  const { data: recent } = useQuery({
    queryKey: ['dashboard-contracts'],
    queryFn: () => api.get<Page<ContractListItem>>('/api/contracts?page=0&size=5'),
  });
  const { data: overdue } = useQuery({
    queryKey: ['dashboard-overdue'],
    queryFn: () => api.get<OverdueRow[]>('/api/collection/overdue'),
  });

  if (isLoading) return <Loading />;
  if (error || !data) return <div className="page"><ErrorBox error={error} /></div>;

  const k = data.kpis;
  const riskTotal = data.riskMix.reduce((s, r) => s + r.count, 0);
  const risk = data.riskMix.map((r) => ({ cat: r.category, pct: riskTotal ? (r.count / riskTotal) * 100 : 0, count: r.count }));
  const monthly = data.monthly.map((m) => ({ m: m.month.slice(5), in: m.collected / 1e6, out: m.issued / 1e6 }));
  const mlnTick = (n: number) => (n === 0 ? '0' : n >= 1 ? String(Math.round(n)) : n.toFixed(1).replace('.', ','));

  return (
    <div className="page">
      <div className="dash-hero">
        <Stat icon="wallet" label={t('totalDebt')} value={fmtMln(k.totalDebt, lang)}
          sub={<>{t('activeContracts')}: <b>{k.activeContracts}</b></>} />
        <Stat icon="trend" tone="gold" label={`${t('totalIn')} · ${t('thisMonth')}`} value={fmtMln(k.collectedThisMonth, lang)}
          sub={k.collectionRate != null ? <>{t('collectionRate')}: <b>{k.collectionRate}%</b></> : <>{t('issued')}: {fmtMln(k.issuedThisMonth, lang)}</>} />
        <Stat icon="alert" tone="danger" label={t('overdue')} value={fmtMln(k.overdue, lang)}
          sub={<>{t('st_late')}: <b>{k.lateContracts}</b></>} />
      </div>

      <div className="dash-mini">
        <MiniStat icon="clock" tone="info" label={t('todayPay')} value={k.todayPayments} />
        <MiniStat icon="calendar" label={t('tomorrowPay')} value={k.tomorrowPayments} />
        <MiniStat icon="contracts" label={t('contracts')} value={k.activeContracts + k.lateContracts} />
      </div>

      <div className="dash-grid" style={{ marginTop: 22 }}>
        <Card>
          <CardHead title={t('monthlyFlow')} sub={lang === 'ru' ? 'млн сум' : "mln so'm"}
            right={<>
              <span className="legend"><i style={{ background: 'var(--primary)' }} />{t('inflow')}</span>
              <span className="legend"><i style={{ background: 'var(--gold)' }} />{t('outflow')}</span>
            </>} />
          <div className="card-pad">
            <BarsChart data={monthly} fmt={mlnTick} keys={[
              { key: 'in', label: t('inflow'), color: 'var(--primary)' },
              { key: 'out', label: t('outflow'), color: 'var(--gold)' },
            ]} />
          </div>
        </Card>

        <Card>
          <CardHead title={t('riskMix')} />
          <div className="card-pad row" style={{ gap: 26, alignItems: 'center', justifyContent: 'center' }}>
            <Donut data={risk} center={
              <>
                <b className="display" style={{ fontSize: 28, lineHeight: 1 }}>{riskTotal}</b>
                <span className="faint" style={{ fontSize: 11.5 }}>{t('contracts').toLowerCase()}</span>
              </>
            } />
            <div className="col" style={{ gap: 10 }}>
              {risk.map((r) => (
                <span key={r.cat} className="legend" style={{ fontSize: 13.5 }}>
                  <i style={{ background: `var(--risk-${r.cat.toLowerCase()})` }} />
                  <b style={{ color: 'var(--text)' }}>{r.cat}</b>
                  <span className="tnum">· {r.count} ({Math.round(r.pct)}%)</span>
                </span>
              ))}
              {risk.length === 0 && <span className="faint">{t('empty')}</span>}
            </div>
          </div>
        </Card>
      </div>

      <div className="dash-split">
        <Card>
          <CardHead title={t('recentContracts')}
            right={<Btn variant="ghost" size="sm" onClick={() => nav('/contracts')}>{t('all')}</Btn>} />
          <div className="dash-list card-body">
            {recent?.content.map((c) => (
              <button key={c.id} className="dash-row" onClick={() => nav(`/contracts/${c.id}`)}>
                <Avatar name={c.clientName} size={36} />
                <span className="dr-main">
                  <b>{c.clientName}</b>
                  <span>{c.contractNo} · {c.productName}</span>
                </span>
                <span className="dr-end">
                  <b>{fmtMln(c.installmentTotal, lang)}</b>
                  <ContractStatusBadge status={c.status} />
                </span>
              </button>
            ))}
            {recent && recent.content.length === 0 && <Empty />}
          </div>
        </Card>

        <Card>
          <CardHead title={t('overdue')}
            right={<Btn variant="ghost" size="sm" onClick={() => nav('/collection')}>{t('all')}</Btn>} />
          <div className="dash-list card-body">
            {overdue?.slice(0, 5).map((r) => (
              <button key={r.contractId} className="dash-row" onClick={() => nav('/collection')}>
                <span className="dr-main">
                  <b>{r.clientName}</b>
                  <span>{r.contractNo} · {r.daysLate} {t('daysLateShort')}</span>
                </span>
                <span className="dr-end">
                  <b style={{ color: 'var(--danger)' }}>{fmtMln(r.overdueAmount, lang)}</b>
                  {r.lastActionDate && <span className="faint" style={{ fontSize: 11.5 }}>{fmtDate(r.lastActionDate, lang)}</span>}
                </span>
              </button>
            ))}
            {overdue && overdue.length === 0 && <Empty>{t('noOverdue')}</Empty>}
          </div>
        </Card>
      </div>
    </div>
  );
}
