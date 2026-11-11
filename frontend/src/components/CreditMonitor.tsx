import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { CreditMonitorReport } from '../api/types';
import { fmtDate, fmtMln } from '../lib/format';
import { Badge, Card, CardHead, Empty, ErrorBox, Loading, Stat } from './ui';

const RATING_TONE = { A: 'success', B: 'success', C: 'warn', D: 'danger', E: 'danger' } as const;

/** KATM va kredit tarixi monitoringi: oxirgi byuro hisoboti, ichki nasiya yuki va qarz yuki (DTI). */
export default function CreditMonitor() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const q = useQuery({ queryKey: ['credit-monitor'], queryFn: () => api.get<CreditMonitorReport>('/api/clients/credit-monitor') });
  if (q.isLoading) return <Loading />;
  const d = q.data;
  if (!d) return <ErrorBox error={q.error} />;
  return (
    <>
      <div className="kpi4" style={{ marginTop: 0 }}>
        <Stat icon="star" tone="info" label={t('cmAvgScore')} value={d.avgScore ?? '—'} sub={`${d.clients} ${t('cmClientsWithReport')}`} />
        <Stat icon="sliders" tone={d.avgDtiPct != null && d.avgDtiPct > d.dtiNormPct ? 'danger' : 'success'} label={t('cmAvgDti')}
          value={d.avgDtiPct != null ? `${d.avgDtiPct}%` : '—'} sub={t('cmDtiNorm', { n: d.dtiNormPct })} />
        <Stat icon="shieldCheck" tone="success" label={t('cmClean')} value={d.cleanClients} sub={t('cmCleanSub')} />
        <Stat icon="alert" tone="danger" label={t('cmFlagged')} value={d.flaggedClients} sub={t('cmFlaggedSub')} />
      </div>
      <Card className="block">
        <CardHead title={t('cmTitle')} sub={t('cmSub')} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('client')}</th><th>{t('cmReport')}</th><th>{t('cmScore')}</th>
              <th className="num">{t('cmExternal')}</th><th className="num">{t('cmInternal')}</th>
              <th className="num">DTI</th><th>{t('cmMaxDelay')}</th>
            </tr></thead>
            <tbody>
              {d.rows.map((r) => {
                const over = r.dtiPct != null && r.dtiPct > d.dtiNormPct;
                return (
                  <tr key={r.clientId} onClick={() => nav(`/clients/${r.clientId}`)}>
                    <td className="cell-main">
                      <div className="col" style={{ gap: 1 }}>
                        <b>{r.fullName} {r.blacklisted && <span className="tag-danger mono">{t('s_BLACKLIST')}</span>}</b>
                        <span className="mono faint" style={{ fontSize: 11 }}>{r.pinfl} · {r.phone}</span>
                      </div>
                    </td>
                    <td data-label={t('cmReport')}>
                      <div className="col" style={{ gap: 1 }}>
                        <span style={{ fontSize: 12 }}>{r.source}</span>
                        <span className="faint" style={{ fontSize: 11 }}>{fmtDate(r.reportDate, lang)}</span>
                      </div>
                    </td>
                    <td data-label={t('cmScore')}>
                      <div className="row" style={{ gap: 6 }}>
                        {r.rating && <Badge tone={RATING_TONE[r.rating as keyof typeof RATING_TONE] ?? 'neutral'}>{r.rating}</Badge>}
                        <span className="mono">{r.score ?? '—'}</span>
                      </div>
                    </td>
                    <td className="num" data-label={t('cmExternal')}>
                      <div className="col" style={{ gap: 1 }}>
                        <span className="mono">{fmtMln(r.externalMonthly, lang)}</span>
                        <span className="faint" style={{ fontSize: 11 }}>{r.activeLoans} {t('pcs')} · {fmtMln(r.externalDebt, lang)}</span>
                      </div>
                    </td>
                    <td className="num" data-label={t('cmInternal')}>
                      <div className="col" style={{ gap: 1 }}>
                        <span className="mono">{fmtMln(r.internalMonthly, lang)}</span>
                        <span className="faint" style={{ fontSize: 11 }}>{fmtMln(r.internalDebt, lang)}</span>
                      </div>
                    </td>
                    <td className="num" data-label="DTI">
                      {r.dtiPct != null ? <Badge tone={over ? 'danger' : r.dtiPct > d.dtiNormPct * 0.8 ? 'warn' : 'success'}>{r.dtiPct}%</Badge> : '—'}
                    </td>
                    <td data-label={t('cmMaxDelay')}>
                      {r.overdueFlag ? <Badge tone="danger" dot>{t('cmOverdueNow')}</Badge>
                        : r.maxOverdueDays > 0 ? <span>{r.maxOverdueDays} {t('daysShort')}</span> : <Badge tone="success">{t('cmNoDelay')}</Badge>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        {d.rows.length === 0 && <Empty>{t('cmEmpty')}</Empty>}
      </Card>
    </>
  );
}
