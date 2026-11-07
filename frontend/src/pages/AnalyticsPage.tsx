import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { AnalyticsReport } from '../api/types';
import { Badge, Card, CardHead, Empty, ErrorBox, Icon, Loading, Seg, Stat } from '../components/ui';
import { Legend, LineChart, Progress, type Series } from '../components/viz';
import { useBranch } from '../lib/branch';
import { fmtMln, fmtMonth } from '../lib/format';

const AGING_COLOR: Record<string, string> = {
  current: 'var(--success)', d1_30: 'var(--gold)', d31_60: '#e8833a', d61_90: 'var(--danger)', d90: '#7f1d1d',
};

/** Kechikish foizi: 5% gacha yaxshi, 15% dan keyin xavfli. */
function PctBadge({ v }: { v?: number }) {
  if (v == null) return <span className="faint">—</span>;
  return <Badge tone={v < 5 ? 'success' : v < 15 ? 'warn' : 'danger'}>{v.toFixed(1)}%</Badge>;
}

export default function AnalyticsPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const branch = useBranch();
  const [months, setMonths] = useState('12');
  const { data, isLoading, error } = useQuery({
    queryKey: ['analytics', months, branch.param],
    queryFn: () => api.get<AnalyticsReport>(`/api/analytics${qs({ months, branchId: branch.param })}`),
  });
  if (isLoading) return <Loading />;
  if (error || !data) return <div className="page"><ErrorBox error={error} /></div>;

  const mln = (n: number) => (n / 1e6).toFixed(Math.abs(n) >= 1e8 ? 0 : 1).replace('.', ',');
  const unit = lang === 'ru' ? 'млн сум' : "mln so'm";
  const pnl = data.pnl;
  const monthly = data.monthly.map((m) => ({ ...m, label: fmtMonth(m.month, lang, true) }));
  const series: Series[] = [
    { key: 'issued', label: t('anIssued'), color: 'var(--gold)', type: 'bar' },
    { key: 'collected', label: t('anCollected'), color: 'var(--primary)', type: 'line' },
    { key: 'profit', label: t('anProfit'), color: 'var(--success)', type: 'line', dashed: true },
    ...(data.partners.length ? [{ key: 'partnerVolume', label: t('anPartnerVolume'), color: 'var(--info)', type: 'line' as const }] : []),
  ];
  const catName = (c: AnalyticsReport['categories'][number]) => (lang === 'ru' ? c.nameRu : c.nameUz);
  const maxVol = Math.max(1, ...data.categories.map((c) => c.volume));

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="analytics" size={18} /></span>{t('analyticsTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('analyticsSub')}</span>
        </div>
        <Seg value={months} onChange={setMonths} options={[
          { value: '6', label: `6 ${t('monthsShort')}` }, { value: '12', label: `12 ${t('monthsShort')}` }, { value: '24', label: `24 ${t('monthsShort')}` },
        ]} />
      </div>

      <div className="kpi4">
        <Stat icon="trend" tone="success" label={t('anRealizedProfit')} value={fmtMln(pnl.profit, lang)} sub={t('anRealizedProfitSub')} />
        <Stat icon="wallet" tone="gold" label="OPEX" value={fmtMln(pnl.opex, lang)} sub={`${pnl.months} ${t('months')}`} />
        <Stat icon="analytics" tone={pnl.operatingProfit < 0 ? 'danger' : 'success'} label={t('anOperatingProfit')} value={fmtMln(pnl.operatingProfit, lang)}
          sub={pnl.partnerCommission > 0 ? <>{t('anPartnerCommission')}: <b>{fmtMln(pnl.partnerCommission, lang)}</b></> : t('anOperatingSub')} />
        <Stat icon="shield" tone="info" label={t('anEcl')} value={fmtMln(pnl.eclProvision, lang)}
          sub={<>{t('anAfterProvision')}: <b>{fmtMln(pnl.profitAfterProvision, lang)}</b></>} />
      </div>

      <Card className="block">
        <CardHead title={t('anDynamics')} sub={`${t('anDynamicsSub')} · ${unit}`} right={<Legend series={series} />} />
        <div className="card-pad"><LineChart data={monthly} series={series} height={280} fmt={mln} /></div>
      </Card>

      <>
        <Card className="block">
          <CardHead title={t('anAging')} sub={t('anAgingSub')} />
          <div className="card-pad col" style={{ gap: 12 }}>
            {data.aging.map((a) => (
              <div key={a.key} className="col" style={{ gap: 5 }}>
                <div className="row between" style={{ fontSize: 12.5 }}>
                  <span><b>{t(`aging_${a.key}`)}</b> <span className="faint">· {a.contracts} {t('pcs')}</span></span>
                  <span className="mono"><b>{fmtMln(a.debt, lang)}</b> <span className="faint">{a.sharePct != null ? `${a.sharePct}%` : ''}</span></span>
                </div>
                <Progress pct={a.sharePct ?? 0} tone={AGING_COLOR[a.key]} />
              </div>
            ))}
          </div>
        </Card>
        <Card className="block">
          <CardHead title={t('anTerms')} sub={t('anTermsSub')} />
          <div className="table-wrap">
            <table className="tbl">
              <thead><tr>
                <th>{t('term')}</th><th className="num">{t('contracts')}</th><th className="num">{t('anVolume')}</th>
                <th className="num">{t('markup')}</th><th className="num">{t('overdue')}</th><th className="num">NPL</th>
              </tr></thead>
              <tbody>
                {data.terms.map((r) => (
                  <tr key={r.bucket} style={{ cursor: 'default' }}>
                    <td className="cell-main"><b>{r.bucket} {t('months')}</b></td>
                    <td className="num mono" data-label={t('contracts')}>{r.contracts}</td>
                    <td className="num mono" data-label={t('anVolume')}>{fmtMln(r.volume, lang)}</td>
                    <td className="num mono" data-label={t('markup')}>{r.avgMarkupPct != null ? `${r.avgMarkupPct}%` : '—'}</td>
                    <td className="num" data-label={t('overdue')}><PctBadge v={r.overduePct} /></td>
                    <td className="num" data-label="NPL"><PctBadge v={r.nplPct} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.terms.length === 0 && <Empty />}
        </Card>
      </>

      <Card className="block">
        <CardHead title={t('anCategories')} sub={t('anCategoriesSub')} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('category')}</th><th className="num">{t('anVolume')}</th><th className="num">{t('anShare')}</th>
              <th className="num">{t('markup')}</th><th className="num">{t('term')}</th>
              <th className="num">{t('anExpectedProfit')}</th><th className="num">{t('anEarnedProfit')}</th>
              <th className="num">{t('anDebt')}</th><th className="num">{t('overdue')}</th><th className="num">NPL</th>
            </tr></thead>
            <tbody>
              {data.categories.map((c) => (
                <tr key={c.code} style={{ cursor: 'default' }}>
                  <td className="cell-main">
                    <div className="col" style={{ gap: 4, minWidth: 130 }}>
                      <b>{catName(c)}</b>
                      <Progress pct={(c.volume / maxVol) * 100} height={4} />
                    </div>
                  </td>
                  <td className="num mono" data-label={t('anVolume')}>{fmtMln(c.volume, lang)}</td>
                  <td className="num mono" data-label={t('anShare')}>{c.sharePct != null ? `${c.sharePct}%` : '—'}</td>
                  <td className="num mono" data-label={t('markup')}>{c.avgMarkupPct != null ? `${c.avgMarkupPct}%` : '—'}</td>
                  <td className="num mono" data-label={t('term')}>{c.avgTermMonths ?? '—'}</td>
                  <td className="num mono" data-label={t('anExpectedProfit')}>{fmtMln(c.expectedProfit, lang)}</td>
                  <td className="num mono" data-label={t('anEarnedProfit')} style={{ color: 'var(--success)' }}>{fmtMln(c.earnedProfit, lang)}</td>
                  <td className="num mono" data-label={t('anDebt')}>{fmtMln(c.debt, lang)}</td>
                  <td className="num" data-label={t('overdue')}><PctBadge v={c.overduePct} /></td>
                  <td className="num" data-label="NPL"><PctBadge v={c.nplPct} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data.categories.length === 0 && <Empty />}
      </Card>

      <>
        <Card className="block">
          <CardHead title={t('anBranches')} sub={t('anBranchesSub')} />
          <div className="table-wrap">
            <table className="tbl">
              <thead><tr>
                <th>{t('branch')}</th><th className="num">{t('contracts')}</th><th className="num">{t('anVolume')}</th>
                <th className="num">{t('anCollected')}</th><th className="num">{t('overdue')}</th><th className="num">{t('anPlan')}</th>
              </tr></thead>
              <tbody>
                {data.branches.map((b, i) => (
                  <tr key={b.id} style={{ cursor: 'default' }}>
                    <td className="cell-main"><b><span className="faint mono">{i + 1}.</span> {b.name}</b></td>
                    <td className="num mono" data-label={t('contracts')}>{b.contracts}</td>
                    <td className="num mono" data-label={t('anVolume')}>{fmtMln(b.volume, lang)}</td>
                    <td className="num mono" data-label={t('anCollected')}>{fmtMln(b.collected, lang)}</td>
                    <td className="num" data-label={t('overdue')}><PctBadge v={b.overduePct} /></td>
                    <td className="num mono" data-label={t('anPlan')}>{b.planPct != null ? `${b.planPct}%` : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.branches.length === 0 && <Empty />}
        </Card>
        <Card className="block">
          <CardHead title={t('anManagers')} sub={t('anManagersSub')} />
          <div className="table-wrap">
            <table className="tbl">
              <thead><tr>
                <th>{t('employee')}</th><th className="num">{t('contracts')}</th><th className="num">{t('anVolume')}</th>
                <th className="num">{t('score')}</th><th className="num">{t('overdue')}</th>
              </tr></thead>
              <tbody>
                {data.managers.map((m, i) => (
                  <tr key={m.id} style={{ cursor: 'default' }}>
                    <td className="cell-main">
                      <div className="col" style={{ gap: 1 }}>
                        <b><span className="faint mono">{i + 1}.</span> {m.name}</b>
                        {m.branch && <span className="faint" style={{ fontSize: 11 }}>{m.branch}</span>}
                      </div>
                    </td>
                    <td className="num mono" data-label={t('contracts')}>{m.contracts}</td>
                    <td className="num mono" data-label={t('anVolume')}>{fmtMln(m.volume, lang)}</td>
                    <td className="num mono" data-label={t('score')}>{m.avgScore ?? '—'}</td>
                    <td className="num" data-label={t('overdue')}><PctBadge v={m.overduePct} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.managers.length === 0 && <Empty />}
        </Card>
      </>

      {data.partners.length > 0 && (
        <Card className="block">
          <CardHead title={t('anPartners')} sub={t('anPartnersSub')} />
          <div className="table-wrap">
            <table className="tbl">
              <thead><tr><th>{t('provider')}</th><th className="num">{t('anDeals')}</th><th className="num">{t('anVolume')}</th><th className="num">{t('commission')}</th></tr></thead>
              <tbody>
                {data.partners.map((p) => (
                  <tr key={p.code} style={{ cursor: 'default' }}>
                    <td className="cell-main"><b>{p.name}</b></td>
                    <td className="num mono" data-label={t('anDeals')}>{p.deals}</td>
                    <td className="num mono" data-label={t('anVolume')}>{fmtMln(p.volume, lang)}</td>
                    <td className="num mono" data-label={t('commission')}>{fmtMln(p.commission, lang)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
