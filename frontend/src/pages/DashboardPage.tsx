import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { api } from '../api/client';
import type { ContractListItem, Dashboard, Page } from '../api/types';
import {
  BarsChart, Btn, Card, CardHead, ContractStatusBadge, Donut, Empty, ErrorBox, Icon, Loading, Stat,
} from '../components/ui';
import { fmtMln } from '../lib/format';

type Kpis = Dashboard['kpis'];

/**
 * Portfel salomatlik indeksi (0–100): shu oy to'lanishi kerak bo'lganning yig'ilgani,
 * muddati o'tgan qarz va NPL (90+ kun) ulushi, kechikkan shartnomalar ulushi bo'yicha jarima.
 */
function healthScore(k: Kpis): number {
  const debt = k.totalDebt || 0;
  const open = k.activeContracts + k.lateContracts;
  const collection = k.collectionRate ?? 100;
  const overdueShare = debt ? (k.overdue / debt) * 100 : 0;
  const nplShare = debt ? (k.npl90Debt / debt) * 100 : 0;
  const lateShare = open ? (k.lateContracts / open) * 100 : 0;
  const score = 100 - (100 - collection) * 0.4 - overdueShare * 1.5 - nplShare * 2 - lateShare * 0.3;
  return Math.max(0, Math.min(100, Math.round(score)));
}

const pct = (part: number, whole: number) => (whole > 0 ? (part / whole) * 100 : 0);
const pct1 = (n: number) => n.toFixed(1).replace('.', ',');

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

  if (isLoading) return <Loading />;
  if (error || !data) return <div className="page"><ErrorBox error={error} /></div>;

  const k = data.kpis;
  const pf = data.portfolio;
  const health = healthScore(k);
  const healthKey = health >= 80 ? 'health_good' : health >= 60 ? 'health_ok' : 'health_bad';
  const healthTone = health >= 80 ? 'success' : health >= 60 ? 'warn' : 'danger';
  const open = k.activeContracts + k.lateContracts;

  const riskTotal = data.riskMix.reduce((s, r) => s + r.count, 0);
  const risk = data.riskMix.map((r) => ({ cat: r.category, pct: pct(r.count, riskTotal), count: r.count }));
  const riskCount = (cats: string[]) => data.riskMix.filter((r) => cats.includes(r.category)).reduce((s, r) => s + r.count, 0);

  const mlnTick = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(1).replace('.', ','));
  const monthly = data.monthly.map((m) => ({ m: m.month.slice(5), in: m.collected / 1e6, out: m.issued / 1e6 }));
  const forecast = data.profitForecast.map((m) => ({ m: m.month.slice(5), p: m.profit / 1e6 }));
  const earnedPct = Math.round(pct(pf.earnedProfit, pf.expectedProfit));
  const catDebt = data.categoryMix.reduce((s, c) => s + c.debt, 0);
  const mlnUnit = lang === 'ru' ? 'млн сум' : "mln so'm";

  return (
    <div className="page">
      {/* Portfel salomatligi va tezkor o'tishlar */}
      <div className="health-banner card anim">
        <div className="row" style={{ gap: 14, minWidth: 0 }}>
          <div className={`health-score hs-${healthTone}`}>{health}</div>
          <div className="col" style={{ gap: 3, minWidth: 0 }}>
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <b style={{ fontSize: 14 }}>{t('healthTitle')}</b>
              <span className={`mono health-tag ht-${healthTone}`}>{t(healthKey)}</span>
            </div>
            <div className="health-meta">
              <span>{t('activeContracts')}: <b>{open}</b></span>
              <span>{t('collectionRate')}: <b>{k.collectionRate != null ? `${k.collectionRate}%` : '—'}</b></span>
              <span>{t('nplShort')}: <b>{pct1(pct(k.npl90Debt, k.totalDebt))}%</b></span>
            </div>
          </div>
        </div>
        <div className="row" style={{ gap: 8 }}>
          <Btn size="sm" icon="newcredit" onClick={() => nav('/new-credit')}>{t('newCredit')}</Btn>
          <Btn size="sm" variant="ghost" icon="collection" onClick={() => nav('/collection')}>{t('collection')}</Btn>
        </div>
      </div>

      <div className="kpi4">
        <Stat icon="wallet" tone="success" label={t('totalDebtLong')} value={fmtMln(k.totalDebt, lang)}
          sub={<>{t('issuedThisMonth')}: <b>{fmtMln(k.issuedThisMonth, lang)}</b></>} />
        <Stat icon="trend" tone="success" label={t('collectedThisMonth')} value={fmtMln(k.collectedThisMonth, lang)}
          sub={<>{t('collectionRate')}: <b>{k.collectionRate != null ? `${k.collectionRate}%` : '—'}</b></>} />
        <Stat icon="alert" tone="gold" label={t('overdueDebts')} value={fmtMln(k.overdue, lang)}
          sub={<>{t('st_late')}: <b>{k.lateContracts}</b> · {t('portfolioShare', { pct: pct1(pct(k.overdue, k.totalDebt)) })}</>} />
        <Stat icon="clock" label={t('todayTomorrow')} value={`${k.todayPayments + k.tomorrowPayments} ${t('pcs')}`}
          sub={<>{t('today')}: <b>{k.todayPayments}</b> · {t('tomorrow')}: <b>{k.tomorrowPayments}</b></>} />
      </div>

      <Card className="block">
        <CardHead title={t('profitTitleLong')}
          sub={`${t('avgMarkup')}: ${pf.avgMarkupPct ?? '—'}% · ${t('avgTerm')}: ${pf.avgTermMonths ?? '—'} ${t('months')}`}
          right={<span className="mono faint" style={{ fontSize: 11.5 }}>{t('avgTicket')}: {fmtMln(pf.avgTicket, lang)}</span>} />
        <div className="card-pad">
          <div className="well-grid">
            <div className="well">
              <span className="well-label">{t('expectedProfit')}</span>
              <b className="well-val">{fmtMln(pf.expectedProfit, lang)}</b>
              <span className="well-sub">{t('inContracts')}</span>
            </div>
            <div className="well">
              <span className="well-label">{t('earnedProfit')}</span>
              <b className="well-val" style={{ color: 'var(--success)' }}>{fmtMln(pf.earnedProfit, lang)}</b>
              <span className="well-sub" style={{ color: 'var(--success)' }}>{t('realizedPct', { pct: earnedPct })}</span>
            </div>
            <div className="well">
              <span className="well-label">{t('remainingProfit')}</span>
              <b className="well-val" style={{ color: 'var(--gold)' }}>{fmtMln(pf.remainingProfit, lang)}</b>
              <span className="well-sub" style={{ color: 'var(--gold)' }}>{t('comesLater')}</span>
            </div>
            <div className="well">
              <span className="well-label">{t('contractsTotal')}</span>
              <b className="well-val">{pf.contracts} {t('pcs')}</b>
              <span className="well-sub">{t('thisMonth')}: {pf.contractsThisMonth} · {t('st_closed')}: {pf.closedContracts}</span>
            </div>
          </div>
          <div className="row between mono" style={{ fontSize: 12, margin: '18px 0 6px' }}>
            <span className="faint">{t('realization')}</span>
            <b style={{ color: 'var(--success)' }}>{earnedPct}%</b>
          </div>
          <div className="profit-bar"><i style={{ width: `${earnedPct}%` }} /></div>
        </div>
        <div className="card-pad profit-forecast">
          <div className="row between" style={{ marginBottom: 14 }}>
            <b style={{ fontSize: 13 }}>{t('profitForecast')}</b>
            <span className="faint mono" style={{ fontSize: 11 }}>{mlnUnit}</span>
          </div>
          <BarsChart data={forecast} height={140} fmt={mlnTick}
            keys={[{ key: 'p', label: t('profit'), color: 'var(--gold)' }]} />
        </div>
      </Card>

      <div className="grid-2-1 block">
        <Card>
          <CardHead title={t('monthlyFlowLong')} sub={`${t('flowSub')} · ${mlnUnit}`}
            right={<>
              <span className="legend"><i style={{ background: 'var(--primary)' }} />{t('inflow')}</span>
              <span className="legend"><i style={{ background: 'var(--gold)' }} />{t('outflow')}</span>
            </>} />
          <div className="card-pad">
            <BarsChart data={monthly} height={230} fmt={mlnTick} keys={[
              { key: 'in', label: t('inflow'), color: 'var(--primary)' },
              { key: 'out', label: t('outflow'), color: 'var(--gold)' },
            ]} />
          </div>
        </Card>

        <Card>
          <CardHead title={t('riskMixLong')} sub={t('riskSub')}
            right={<button className="link-btn" onClick={() => nav('/collection')}>{t('details')}</button>} />
          <div className="card-pad col center" style={{ gap: 16 }}>
            <Donut data={risk} size={170} center={
              <>
                <b className="mono" style={{ fontSize: 24, lineHeight: 1 }}>{riskTotal} {t('pcs')}</b>
                <span className="faint" style={{ fontSize: 11 }}>{t('contracts').toLowerCase()}</span>
              </>
            } />
            <div className="row" style={{ gap: 12, flexWrap: 'wrap', justifyContent: 'center' }}>
              {risk.map((r) => (
                <span key={r.cat} className="legend">
                  <i style={{ background: `var(--risk-${r.cat.toLowerCase()})` }} />
                  <b style={{ color: 'var(--text)' }}>{r.cat}</b>
                  <span className="mono">{Math.round(r.pct)}%</span>
                </span>
              ))}
              {risk.length === 0 && <span className="faint">{t('empty')}</span>}
            </div>
            <div className="risk-foot">
              <div className="row between"><span>{t('riskLowA')}</span><b className="mono" style={{ color: 'var(--success)' }}>{riskCount(['A'])} {t('pcs')}</b></div>
              <div className="row between"><span>{t('riskHighCD')}</span><b className="mono" style={{ color: 'var(--gold)' }}>{riskCount(['C', 'D'])} {t('pcs')}</b></div>
            </div>
          </div>
        </Card>
      </div>

      <div className="grid-2 block">
        <Card>
          <CardHead title={t('recentContracts')}
            right={<button className="link-btn" onClick={() => nav('/contracts')}>{t('viewAll')} <Icon name="chevR" size={13} /></button>} />
          <div className="dash-list card-body">
            {recent?.content.map((c) => (
              <button key={c.id} className="dash-row" onClick={() => nav(`/contracts/${c.id}`)}>
                <span className="dr-main">
                  <b>{c.clientName}</b>
                  <span className="mono">{c.contractNo} · {c.productName}</span>
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
          <CardHead title={t('categoryShare')} sub={t('categoryShareSub')} />
          <div className="card-pad col" style={{ gap: 14 }}>
            {data.categoryMix.map((c) => {
              const share = pct(c.debt, catDebt);
              return (
                <div key={c.code} className="col" style={{ gap: 6 }}>
                  <div className="row between" style={{ fontSize: 12.5 }}>
                    <span style={{ fontWeight: 600 }}>{lang === 'ru' ? c.nameRu : c.nameUz}
                      <span className="faint" style={{ fontWeight: 400 }}> · {c.contracts} {t('pcs')}</span></span>
                    <span className="mono faint">{fmtMln(c.debt, lang)} ({Math.round(share)}%)</span>
                  </div>
                  <div className="bar"><i style={{ width: `${share}%` }} /></div>
                </div>
              );
            })}
            {data.categoryMix.length === 0 && <Empty />}
          </div>
        </Card>
      </div>
    </div>
  );
}
