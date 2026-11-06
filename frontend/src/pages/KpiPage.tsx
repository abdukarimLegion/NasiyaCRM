import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { KpiConfig, KpiHistoryPoint, KpiMetric, KpiReport, KpiTargets, OperatorKpi } from '../api/types';
import { Avatar, Badge, Btn, Card, CardHead, Empty, ErrorBox, Field, Icon, Loading, Modal, Stat } from '../components/ui';
import { Progress, Ring } from '../components/viz';
import { GRADE_COLOR } from '../lib/grade';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { currentMonth, fmtMln, fmtMonth, fmtNum, fmtSom } from '../lib/format';

const METRICS: KpiMetric[] = ['CONTRACTS', 'SALES', 'COLLECTED', 'RECOVERED', 'ACTIONS', 'QUALITY'];
const MONEY: KpiMetric[] = ['SALES', 'COLLECTED', 'RECOVERED'];

function metricValue(m: KpiMetric, v: number | undefined, lang: string): string {
  if (v == null) return '—';
  if (MONEY.includes(m)) return fmtMln(v, lang);
  if (m === 'QUALITY') return `${v}%`;
  return fmtNum(v);
}

export default function KpiPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can, user } = useAuth();
  const branch = useBranch();
  const [month, setMonth] = useState(currentMonth);
  const [open, setOpen] = useState<OperatorKpi | null>(null);
  const [cfgOpen, setCfgOpen] = useState(false);
  const all = can('KPI_VIEW_ALL');

  const { data, isLoading, error } = useQuery({
    queryKey: ['kpi', month, branch.param],
    queryFn: () => api.get<KpiReport>(`/api/kpi${qs({ month, branchId: branch.param })}`),
  });

  if (isLoading) return <Loading />;
  if (error || !data) return <div className="page"><ErrorBox error={error} /></div>;
  const s = data.summary;
  const rows = data.operators;
  const top = rows.filter((o) => o.score != null).slice(0, 3);
  const me = rows.find((o) => o.userId === user?.id);

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="target" size={18} /></span>{all ? t('kpiTitle') : t('kpiMine')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('kpiSub')}</span>
        </div>
        <div className="row" style={{ gap: 8 }}>
          <input className="input" type="month" value={month} max={currentMonth()} style={{ width: 160 }}
            onChange={(e) => e.target.value && setMonth(e.target.value)} aria-label={t('month')} />
          {can('KPI_MANAGE') && <Btn variant="ghost" size="sm" icon="sliders" onClick={() => setCfgOpen(true)}>{t('kpiFormula')}</Btn>}
        </div>
      </div>

      {all ? (
        <>
          <div className="kpi4">
            <div className="stat anim kpi-avg" data-tone="primary">
              <Ring value={s.avgScore} size={58} stroke={6} color={s.avgScore != null ? GRADE_COLOR[grade(s.avgScore)] : undefined}>
                <b className="mono">{s.avgScore != null ? Math.round(s.avgScore) : '—'}</b>
              </Ring>
              <div className="col" style={{ gap: 3 }}>
                <span className="stat-label">{t('kpiAvg')}</span>
                <div className="row" style={{ gap: 5 }}>
                  {(['A', 'B', 'C', 'D'] as const).map((g) => (
                    <span key={g} className="grade-chip" style={{ color: GRADE_COLOR[g] }}>{g} · {s.grades[g]}</span>
                  ))}
                </div>
              </div>
            </div>
            <Stat icon="award" tone="gold" label={t('kpiBonusFund')} value={fmtMln(s.totalBonus, lang)}
              sub={<>{t('kpiOperators')}: <b>{s.operators}</b></>} />
            <Stat icon="newcredit" tone="success" label={t('kpiSalesTotal')} value={fmtMln(s.totalSales, lang)}
              sub={fmtMonth(s.month, lang)} />
            <Stat icon="collection" tone="info" label={t('kpiRecoveredTotal')} value={fmtMln(s.totalRecovered, lang)}
              sub={<>{t('kpiCollectedTotal')}: <b>{fmtMln(s.totalCollected, lang)}</b></>} />
          </div>

          {top.length > 0 && (
            <div className="podium block">
              {top.map((o) => (
                <button key={o.userId} className={`podium-card pc-${o.rank}`} onClick={() => setOpen(o)}>
                  <span className="podium-rank">{o.rank}</span>
                  <Avatar name={o.fullName} size={42} />
                  <div className="col" style={{ gap: 2, minWidth: 0, flex: 1, textAlign: 'left' }}>
                    <b className="ellipsis">{o.fullName}</b>
                    <span className="faint ellipsis" style={{ fontSize: 12 }}>{t(`r_${o.role}`)}{o.branchName ? ` · ${o.branchName}` : ''}</span>
                    {o.bonusPayout > 0 && <span className="podium-bonus"><Icon name="award" size={13} />{fmtSom(o.bonusPayout, lang)}</span>}
                  </div>
                  <Ring value={o.score} size={62} stroke={6} color={o.grade ? GRADE_COLOR[o.grade] : undefined}>
                    <b className="mono" style={{ fontSize: 15 }}>{o.score != null ? Math.round(o.score) : '—'}</b>
                    <span style={{ fontSize: 10, color: o.grade ? GRADE_COLOR[o.grade] : undefined, fontWeight: 700 }}>{o.grade}</span>
                  </Ring>
                </button>
              ))}
            </div>
          )}

          <Card className="block">
            <CardHead title={t('kpiRanking')} sub={t('kpiRankingSub')} />
            <div className="table-wrap">
              <table className="tbl kpi-tbl">
                <thead><tr>
                  <th style={{ width: 44 }}>#</th>
                  <th>{t('employee')}</th>
                  {METRICS.map((m) => <th key={m} className="num">{t(`km_${m}`)}</th>)}
                  <th className="num">{t('kpiScore')}</th>
                  <th className="num">{t('kpiBonus')}</th>
                </tr></thead>
                <tbody>
                  {rows.map((o) => (
                    <tr key={o.userId} onClick={() => setOpen(o)}>
                      <td className="mono faint">{o.rank || '—'}</td>
                      <td className="cell-main">
                        <div className="cell-client">
                          <Avatar name={o.fullName} size={32} />
                          <div className="col" style={{ gap: 1 }}>
                            <b>{o.fullName}</b>
                            <span>{t(`r_${o.role}`)}{o.branchName ? ` · ${o.branchName}` : ''}</span>
                          </div>
                        </div>
                      </td>
                      {METRICS.map((m) => {
                        const r = o.metrics.find((x) => x.metric === m);
                        return (
                          <td key={m} className="num" data-label={t(`km_${m}`)}>
                            <div className="kpi-cell" data-off={!r?.included}>
                              <span className="mono">{metricValue(m, r?.actual, lang)}</span>
                              {r?.included && <Progress pct={r.achievementPct} height={4} />}
                              {r?.included && m !== 'QUALITY' && <span className="faint mono" style={{ fontSize: 10.5 }}>{Math.round(r.achievementPct ?? 0)}%</span>}
                            </div>
                          </td>
                        );
                      })}
                      <td className="num" data-label={t('kpiScore')}>
                        {o.score != null
                          ? <span className="score-pill" style={{ ['--g' as string]: GRADE_COLOR[o.grade!] }}>{o.score.toFixed(1)}<i>{o.grade}</i></span>
                          : <span className="faint">{t('kpiNoTarget')}</span>}
                      </td>
                      <td className="num mono" data-label={t('kpiBonus')}>{o.bonusPayout > 0 ? fmtMln(o.bonusPayout, lang) : '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {rows.length === 0 && <Empty />}
          </Card>
        </>
      ) : me ? (
        <MyKpi o={me} onOpen={() => setOpen(me)} />
      ) : <Empty />}

      {open && <DetailModal o={open} config={data.config} onClose={() => setOpen(null)} />}
      {cfgOpen && <ConfigModal config={data.config} onClose={() => setCfgOpen(false)} />}
    </div>
  );
}

function grade(score: number) {
  return score >= 100 ? 'A' : score >= 80 ? 'B' : score >= 60 ? 'C' : 'D';
}

function MyKpi({ o, onOpen }: { o: OperatorKpi; onOpen: () => void }) {
  const { t, i18n } = useTranslation();
  return (
    <Card className="block">
      <div className="card-pad my-kpi">
        <Ring value={o.score} size={150} stroke={12} color={o.grade ? GRADE_COLOR[o.grade] : undefined}>
          <b className="mono" style={{ fontSize: 34, lineHeight: 1 }}>{o.score != null ? Math.round(o.score) : '—'}</b>
          <span className="faint" style={{ fontSize: 12 }}>{o.grade ? `${t('grade')} ${o.grade}` : t('kpiNoTarget')}</span>
        </Ring>
        <div className="col" style={{ gap: 12, flex: 1, minWidth: 0 }}>
          <MetricBars o={o} lang={i18n.language} />
          <div className="row" style={{ gap: 10, flexWrap: 'wrap' }}>
            {o.bonusPayout > 0 && <Badge tone="success"><Icon name="award" size={13} /> {t('kpiBonus')}: {fmtSom(o.bonusPayout, i18n.language)}</Badge>}
            <Btn size="sm" variant="ghost" icon="trend" onClick={onOpen}>{t('details')}</Btn>
          </div>
        </div>
      </div>
    </Card>
  );
}

function MetricBars({ o, lang }: { o: OperatorKpi; lang: string }) {
  const { t } = useTranslation();
  const included = o.metrics.filter((m) => m.included);
  if (included.length === 0) return <span className="faint">{t('kpiNoTargetHint')}</span>;
  return (
    <div className="metric-bars">
      {included.map((m) => (
        <div key={m.metric} className="mb-row">
          <div className="row between" style={{ fontSize: 12.5 }}>
            <span><b>{t(`km_${m.metric}`)}</b> <span className="faint">· {t('weight')} {m.weight}</span></span>
            <span className="mono">
              {metricValue(m.metric, m.actual, lang)}
              {m.metric !== 'QUALITY' && <span className="faint"> / {metricValue(m.metric, m.target, lang)}</span>}
              <b style={{ marginLeft: 8 }}>{Math.round(m.achievementPct ?? 0)}%</b>
            </span>
          </div>
          <Progress pct={m.achievementPct} />
        </div>
      ))}
    </div>
  );
}

function DetailModal({ o, config, onClose }: { o: OperatorKpi; config: KpiConfig; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const qc = useQueryClient();
  const history = useQuery({
    queryKey: ['kpi-history', o.userId],
    queryFn: () => api.get<KpiHistoryPoint[]>(`/api/kpi/users/${o.userId}/history?months=6`),
  });
  const [tg, setTg] = useState<KpiTargets>(o.targets);
  const save = useMutation({
    mutationFn: () => api.put(`/api/kpi/targets/${o.userId}`, tg),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['kpi'] });
      void qc.invalidateQueries({ queryKey: ['kpi-history', o.userId] });
      onClose();
    },
  });
  const maxScore = Math.max(100, ...(history.data ?? []).map((h) => h.score ?? 0));
  const num = (k: keyof KpiTargets, label: string, hint?: string) => (
    <Field label={label} hint={hint}>
      <input className="input mono" type="number" min={0} value={tg[k]}
        onChange={(e) => setTg({ ...tg, [k]: Number(e.target.value) || 0 })} />
    </Field>
  );

  return (
    <Modal wide title={o.fullName} onClose={onClose}
      footer={can('KPI_MANAGE') ? <>
        <Btn variant="ghost" onClick={onClose}>{t('close')}</Btn>
        <Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('saveTargets')}</Btn>
      </> : <Btn variant="ghost" onClick={onClose}>{t('close')}</Btn>}>
      <div className="col" style={{ gap: 18 }}>
        <div className="row" style={{ gap: 18, alignItems: 'center', flexWrap: 'wrap' }}>
          <Ring value={o.score} size={96} stroke={9} color={o.grade ? GRADE_COLOR[o.grade] : undefined}>
            <b className="mono" style={{ fontSize: 22 }}>{o.score != null ? Math.round(o.score) : '—'}</b>
            <span style={{ fontSize: 11, fontWeight: 700, color: o.grade ? GRADE_COLOR[o.grade] : undefined }}>{o.grade}</span>
          </Ring>
          <div className="col" style={{ gap: 6, flex: 1, minWidth: 200 }}>
            <span className="faint">{t(`r_${o.role}`)}{o.branchName ? ` · ${o.branchName}` : ''}</span>
            <div className="row" style={{ gap: 6, flexWrap: 'wrap' }}>
              {o.rank > 0 && <Badge tone="info">#{o.rank}</Badge>}
              {o.openContracts > 0 && <Badge tone="neutral">{t('kpiPortfolio')}: {o.openContracts} · {t('st_late')}: {o.lateContracts}</Badge>}
              {o.promisesMade > 0 && <Badge tone={o.promisesKept / o.promisesMade >= 0.6 ? 'success' : 'warn'}>{t('kpiPromises')}: {o.promisesKept}/{o.promisesMade}</Badge>}
              <Badge tone={o.bonusPayout > 0 ? 'success' : 'neutral'}><Icon name="award" size={13} /> {fmtSom(o.bonusPayout, lang)}</Badge>
            </div>
          </div>
        </div>

        <MetricBars o={o} lang={lang} />

        <div className="col" style={{ gap: 8 }}>
          <b style={{ fontSize: 13 }}>{t('kpiHistory')}</b>
          <div className="hist-bars">
            {history.data?.map((h) => (
              <div key={h.month} className="hb-col" title={`${fmtMonth(h.month, lang)}: ${h.score ?? '—'}`}>
                <span className="mono hb-val">{h.score != null ? Math.round(h.score) : '—'}</span>
                <i style={{ height: `${((h.score ?? 0) / maxScore) * 100}%`, background: h.grade ? GRADE_COLOR[h.grade] : 'var(--surface-3)' }} />
                <span className="hb-x">{fmtMonth(h.month, lang, true)}</span>
              </div>
            ))}
          </div>
          <span className="faint" style={{ fontSize: 11.5 }}>{t('kpiQualityNote')}</span>
        </div>

        {can('KPI_MANAGE') && (
          <>
            <div className="divider" />
            <b style={{ fontSize: 13 }}>{t('kpiTargets')}</b>
            <div className="form-grid">
              {num('contractsCount', t('km_CONTRACTS'))}
              {num('salesVolume', t('km_SALES'), t('somHint'))}
              {num('collectedAmount', t('km_COLLECTED'), t('somHint'))}
              {num('recoveredAmount', t('km_RECOVERED'), t('somHint'))}
              {num('actionsCount', t('km_ACTIONS'))}
              {num('bonusAmount', t('kpiBonusBase'), t('kpiBonusHint', { min: config.minScoreForBonus, cap: config.bonusCapPct }))}
            </div>
            <ErrorBox error={save.error} />
          </>
        )}
      </div>
    </Modal>
  );
}

function ConfigModal({ config, onClose }: { config: KpiConfig; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [c, setC] = useState<KpiConfig>(config);
  const total = METRICS.reduce((a, m) => a + (c.weights[m] ?? 0), 0);
  const save = useMutation({
    mutationFn: () => api.put<KpiConfig>('/api/kpi/config', c),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['kpi'] });
      onClose();
    },
  });
  return (
    <Modal wide title={t('kpiFormula')} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending || total <= 0} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 16 }}>
        <div className="stage-tip st-info"><Icon name="spark" size={18} /><span>{t('kpiFormulaHint')}</span></div>
        <div className="col" style={{ gap: 12 }}>
          {METRICS.map((m) => {
            const w = c.weights[m] ?? 0;
            return (
              <div key={m} className="weight-row">
                <div className="col" style={{ gap: 1, minWidth: 0 }}>
                  <b style={{ fontSize: 13 }}>{t(`km_${m}`)}</b>
                  <span className="faint" style={{ fontSize: 11.5 }}>{t(`kmh_${m}`)}</span>
                </div>
                <input type="range" min={0} max={50} value={w}
                  onChange={(e) => setC({ ...c, weights: { ...c.weights, [m]: Number(e.target.value) } })} />
                <span className="mono weight-val">{w}<span className="faint"> · {total ? Math.round((w / total) * 100) : 0}%</span></span>
              </div>
            );
          })}
        </div>
        <div className="form-grid">
          <Field label={t('kpiMinBonus')} hint="%">
            <input className="input mono" type="number" min={0} max={200} value={c.minScoreForBonus}
              onChange={(e) => setC({ ...c, minScoreForBonus: Number(e.target.value) })} />
          </Field>
          <Field label={t('kpiBonusCap')} hint="%">
            <input className="input mono" type="number" min={50} max={300} value={c.bonusCapPct}
              onChange={(e) => setC({ ...c, bonusCapPct: Number(e.target.value) })} />
          </Field>
          <Field label={t('kpiAchCap')} hint="%">
            <input className="input mono" type="number" min={100} max={300} value={c.achievementCapPct}
              onChange={(e) => setC({ ...c, achievementCapPct: Number(e.target.value) })} />
          </Field>
        </div>
        <ErrorBox error={save.error} />
      </div>
    </Modal>
  );
}
