import { useDeferredValue, useState } from 'react';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { Forecast } from '../api/types';
import { Btn, Card, CardHead, ErrorBox, Icon, Loading, Seg } from '../components/ui';
import type { IconName } from '../components/icons';
import { Legend, LineChart, type Series } from '../components/viz';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { fmtMln, fmtMonth } from '../lib/format';

const INSIGHT_ICON: Record<string, IconName> = {
  ON_TIME_RATE: 'clock', ARREARS_SHARE: 'alert', NEGATIVE_CASH: 'wallet', PEAK_MONTH: 'trend',
  HIGH_SEASON: 'calendar', NO_HISTORY: 'spark',
};

export default function ForecastPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const branch = useBranch();
  const { can } = useAuth();
  const [horizon, setHorizon] = useState('12');
  const [growth, setGrowth] = useState(0);
  const [collectionAdj, setCollectionAdj] = useState(0);
  const [newSales, setNewSales] = useState(true);
  const g = useDeferredValue(growth);
  const c = useDeferredValue(collectionAdj);

  const { data, isLoading, error, isFetching } = useQuery({
    queryKey: ['forecast', horizon, g, c, newSales, branch.param],
    queryFn: () => api.get<Forecast>(`/api/forecast${qs({ horizon, growth: g, collectionAdj: c, newSales, branchId: branch.param })}`),
    placeholderData: keepPreviousData,
  });

  if (isLoading) return <Loading />;
  if (error || !data) return <div className="page"><ErrorBox error={error} /></div>;
  const a = data.assumptions;
  const mln = (n: number) => (Math.abs(n) >= 1e6 ? (n / 1e6).toFixed(Math.abs(n) >= 1e8 ? 0 : 1).replace('.', ',') : (n / 1e6).toFixed(2).replace('.', ','));
  const unit = lang === 'ru' ? 'млн сум' : "mln so'm";

  const flow = data.points.map((p) => ({ ...p, label: fmtMonth(p.month, lang, true) }));
  const flowSeries: Series[] = [
    { key: 'fromSchedule', label: t('fcFromSchedule'), color: 'var(--primary)', type: 'area' },
    { key: 'inflow', label: t('fcInflow'), color: 'var(--info)', type: 'line' },
    { key: 'newSales', label: t('fcNewSales'), color: 'var(--gold)', type: 'bar' },
    { key: 'profit', label: t('fcProfit'), color: 'var(--success)', type: 'line', dashed: true },
  ];
  const debtSeries: Series[] = [
    { key: 'debtEnd', label: t('fcDebtEnd'), color: 'var(--primary)', type: 'area' },
    { key: 'arrearsEnd', label: t('fcArrearsEnd'), color: 'var(--danger)', type: 'area' },
    { key: 'cumulativeNet', label: t('fcCumulativeNet'), color: 'var(--gold)', type: 'line', dashed: true },
  ];

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="trend" size={18} /></span>{t('fcTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('fcSub')}</span>
        </div>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          <Seg value={horizon} onChange={setHorizon} options={[
            { value: '6', label: `6 ${t('monthsShort')}` }, { value: '12', label: `12 ${t('monthsShort')}` },
            { value: '24', label: `24 ${t('monthsShort')}` },
          ]} />
        </div>
      </div>

      <div className="fc-top">
        <Card className="fc-sim">
          <CardHead title={<><Icon name="sliders" size={16} /> {t('fcWhatIf')}</>} sub={t('fcWhatIfSub')}
            right={(growth !== 0 || collectionAdj !== 0) && (
              <button className="link-btn" onClick={() => { setGrowth(0); setCollectionAdj(0); }}>{t('reset')}</button>
            )} />
          <div className="card-pad col" style={{ gap: 18 }}>
            <SliderRow label={t('fcGrowth')} value={growth} min={-50} max={100} step={5} onChange={setGrowth} />
            <SliderRow label={t('fcCollectionAdj')} value={collectionAdj} min={-30} max={20} step={1} onChange={setCollectionAdj} />
            <label className="check-row">
              <input type="checkbox" checked={newSales} onChange={(e) => setNewSales(e.target.checked)} />
              {t('fcIncludeNewSales')}
            </label>
          </div>
        </Card>
        <div className="scenario-grid" data-busy={isFetching}>
          {data.scenarios.map((s) => (
            <div key={s.key} className={`scenario sc-${s.key}`}>
              <span className="scenario-name">{t(`sc_${s.key}`)}</span>
              <span className="faint mono" style={{ fontSize: 11 }}>
                {s.salesGrowthPct > 0 ? '+' : ''}{s.salesGrowthPct}% {t('fcSalesShort')} · {s.collectionAdjPct > 0 ? '+' : ''}{s.collectionAdjPct}% {t('fcCollShort')}
              </span>
              <b className="mono scenario-val">{fmtMln(s.totals.inflow, lang)}</b>
              <span className="faint" style={{ fontSize: 11.5 }}>{t('fcInflowTotal')}</span>
              <div className="scenario-kv">
                <span>{t('fcProfit')}</span><b className="mono">{fmtMln(s.totals.profit, lang)}</b>
                <span>{t('fcNetCash')}</span><b className="mono" style={{ color: s.totals.netCash < 0 ? 'var(--danger)' : 'var(--success)' }}>{fmtMln(s.totals.netCash, lang)}</b>
              </div>
            </div>
          ))}
        </div>
      </div>

      <Card className="block">
        <CardHead title={t('fcCashFlow')} sub={`${t('fcCashFlowSub')} · ${unit}`} right={<Legend series={flowSeries} />} />
        <div className="card-pad">
          <LineChart data={flow} series={flowSeries} height={300} fmt={mln} />
        </div>
      </Card>

      <div className="grid-2-1 block">
        <Card>
          <CardHead title={t('fcDebtTitle')} sub={unit} right={<Legend series={debtSeries} />} />
          <div className="card-pad">
            <LineChart data={flow} series={debtSeries} height={240} fmt={mln} />
          </div>
        </Card>
        <Card>
          <CardHead title={<><Icon name="spark" size={16} /> {t('fcInsights')}</>} />
          <div className="card-pad col" style={{ gap: 10 }}>
            {data.insights.map((ins) => (
              <div key={ins.code} className={`insight in-${ins.level}`}>
                <span className="insight-ic"><Icon name={INSIGHT_ICON[ins.code] ?? 'spark'} size={16} /></span>
                <span>{t(`ins_${ins.code}`, { value: ins.value && /^\d{4}-\d{2}$/.test(ins.value) ? fmtMonth(ins.value, lang) : ins.value })}</span>
              </div>
            ))}
          </div>
        </Card>
      </div>

      <div className="grid-2 block">
        <Card>
          <CardHead title={t('fcAssumptions')} sub={t('fcAssumptionsSub')} />
          <div className="card-pad">
            <dl className="kv fc-kv">
              <dt>{t('fcOnTime')}</dt><dd className="mono">{a.onTimeRatePct}%</dd>
              <dt>{t('fcRecovery')}</dt><dd className="mono">{a.recoveryRatePct}% / {t('monthShort')}</dd>
              <dt>{t('fcBaseSales')}</dt><dd className="mono">{fmtMln(a.baseMonthlySales, lang)}</dd>
              <dt>{t('avgMarkup')}</dt><dd className="mono">{a.avgMarkupPct}%</dd>
              <dt>{t('avgTerm')}</dt><dd className="mono">{a.avgTermMonths} {t('months')}</dd>
              <dt>{t('fcOpenDebt')}</dt><dd className="mono">{fmtMln(a.openDebt, lang)}</dd>
              <dt>{t('overdue')}</dt><dd className="mono" style={{ color: 'var(--danger)' }}>{fmtMln(a.arrears, lang)}</dd>
              <dt>{t('fcHistory')}</dt><dd className="mono">{a.historyMonths} {t('months')}</dd>
            </dl>
          </div>
        </Card>
        <Card>
          <CardHead title={t('fcEcl')} sub={t('fcEclSub')}
            right={<b className="mono" style={{ color: 'var(--gold)' }}>{fmtMln(data.eclTotal, lang)}</b>} />
          <div className="card-pad col" style={{ gap: 12 }}>
            {data.ecl.map((e) => (
              <div key={e.key} className="col" style={{ gap: 5 }}>
                <div className="row between" style={{ fontSize: 12.5 }}>
                  <span><b>{t(`ecl_${e.key}`)}</b> <span className="faint">· {e.ratePct}%</span></span>
                  <span className="mono faint">{fmtMln(e.exposure, lang)} → <b style={{ color: 'var(--text)' }}>{fmtMln(e.reserve, lang)}</b></span>
                </div>
                <div className="bar"><i style={{ width: `${a.openDebt ? (e.exposure / a.openDebt) * 100 : 0}%`, background: `var(--${e.key === 'current' ? 'success' : e.key === 'd1_30' ? 'gold' : 'danger'})` }} /></div>
              </div>
            ))}
            <span className="faint" style={{ fontSize: 11.5 }}>{t('fcEclNote')}</span>
          </div>
        </Card>
      </div>

      <SeasonalityCard key={data.seasonality.join(',')} data={data} editable={can('SETTINGS_EDIT')} />

      <Card className="block">
        <CardHead title={t('fcTable')} sub={unit} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('month')}</th><th className="num">{t('fcSeason')}</th><th className="num">{t('fcScheduled')}</th>
              <th className="num">{t('fcFromSchedule')}</th><th className="num">{t('fcFromArrears')}</th>
              <th className="num">{t('fcNewSales')}</th><th className="num">{t('fcFromNew')}</th>
              <th className="num">{t('fcInflow')}</th><th className="num">{t('fcProfit')}</th><th className="num">{t('fcNetCash')}</th>
            </tr></thead>
            <tbody>
              {data.points.map((p) => (
                <tr key={p.month} style={{ cursor: 'default' }}>
                  <td className="cell-main"><b>{fmtMonth(p.month, lang)}</b></td>
                  <td className="num mono" data-label={t('fcSeason')}>×{p.seasonality.toFixed(2)}</td>
                  <td className="num mono" data-label={t('fcScheduled')}>{mln(p.scheduled)}</td>
                  <td className="num mono" data-label={t('fcFromSchedule')}>{mln(p.fromSchedule)}</td>
                  <td className="num mono" data-label={t('fcFromArrears')}>{mln(p.fromArrears)}</td>
                  <td className="num mono" data-label={t('fcNewSales')}>{mln(p.newSales)}</td>
                  <td className="num mono" data-label={t('fcFromNew')}>{mln(p.fromNewSales)}</td>
                  <td className="num mono" data-label={t('fcInflow')}><b>{mln(p.inflow)}</b></td>
                  <td className="num mono" data-label={t('fcProfit')} style={{ color: 'var(--success)' }}>{mln(p.profit)}</td>
                  <td className="num mono" data-label={t('fcNetCash')} style={{ color: p.netCash < 0 ? 'var(--danger)' : undefined }}>{mln(p.netCash)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="card-pad faint" style={{ fontSize: 11.5 }}>{t('fcNoPenalty')}</div>
      </Card>
    </div>
  );
}

function SliderRow({ label, value, min, max, step, onChange }: {
  label: string; value: number; min: number; max: number; step: number; onChange: (v: number) => void;
}) {
  return (
    <div className="col" style={{ gap: 6 }}>
      <div className="row between" style={{ fontSize: 12.5 }}>
        <span style={{ fontWeight: 600 }}>{label}</span>
        <b className="mono" style={{ color: value > 0 ? 'var(--success)' : value < 0 ? 'var(--danger)' : 'var(--text-faint)' }}>
          {value > 0 ? '+' : ''}{value}%
        </b>
      </div>
      <input type="range" className="range" min={min} max={max} step={step} value={value}
        onChange={(e) => onChange(Number(e.target.value))} />
    </div>
  );
}

function SeasonalityCard({ data, editable }: { data: Forecast; editable: boolean }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [vals, setVals] = useState<number[]>(data.seasonality);
  const dirty = vals.some((v, i) => Math.abs(v - data.seasonality[i]) > 1e-9);
  const done = () => void qc.invalidateQueries({ queryKey: ['forecast'] });
  const save = useMutation({ mutationFn: (v: number[]) => api.put('/api/forecast/seasonality', { values: v }), onSuccess: done });
  const reset = useMutation({ mutationFn: () => api.del('/api/forecast/seasonality'), onSuccess: done });
  const max = Math.max(1.5, ...vals);
  return (
    <Card className="block">
      <CardHead title={t('fcSeasonality')}
        sub={data.customSeasonality ? t('fcSeasonCustom') : t('fcSeasonDefault')}
        right={editable && <>
          {data.suggestedSeasonality && (
            <Btn size="sm" variant="ghost" icon="refresh" onClick={() => setVals(data.suggestedSeasonality!)}>{t('fcSeasonSuggest')}</Btn>
          )}
          {data.customSeasonality && <Btn size="sm" variant="ghost" onClick={() => reset.mutate()}>{t('reset')}</Btn>}
          <Btn size="sm" icon="check" disabled={!dirty || save.isPending} onClick={() => save.mutate(vals)}>{t('save')}</Btn>
        </>} />
      <div className="card-pad">
        <div className="season-grid">
          {vals.map((v, i) => (
            <div key={i} className="season-col" data-hi={v >= 1.15} data-lo={v < 0.95}>
              <div className="season-bar"><i style={{ height: `${(v / max) * 100}%` }} /></div>
              {editable
                ? <input className="season-in mono" type="number" step={0.01} min={0.1} max={5} value={v}
                    onChange={(e) => setVals(vals.map((x, k) => (k === i ? Number(e.target.value) || 0 : x)))} />
                : <span className="mono season-v">{v.toFixed(2)}</span>}
              <span className="season-m">{fmtMonth(`2000-${String(i + 1).padStart(2, '0')}`, i18n.language, true)}</span>
            </div>
          ))}
        </div>
        <ErrorBox error={save.error ?? reset.error} />
        <p className="faint" style={{ fontSize: 11.5, margin: '12px 0 0' }}>{t('fcSeasonHint')}</p>
      </div>
    </Card>
  );
}
