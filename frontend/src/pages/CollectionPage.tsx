import { useMemo, useState, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, Navigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, downloadFile, openPdf, qs } from '../api/client';
import type {
  ActionType, CollectionActionDto, CollectionStage, LegalCaseRow, LegalStatus, OverdueRow, UserBrief,
} from '../api/types';
import { Badge, Btn, Card, Empty, ErrorBox, Field, Icon, Modal, type Tone } from '../components/ui';
import type { IconName } from '../components/icons';
import { Tabs } from '../components/viz';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { daysSince, fmtDate, fmtMln, fmtSom, todayIso } from '../lib/format';

const STAGES: CollectionStage[] = ['REMINDER', 'SOFT', 'HARD', 'LEGAL'];
const STAGE_KEY: Record<CollectionStage, string> = { REMINDER: 'reminder', SOFT: 'soft', HARD: 'hard', LEGAL: 'legal' };
const STAGE_TONE: Record<CollectionStage, Tone> = { REMINDER: 'info', SOFT: 'warn', HARD: 'danger', LEGAL: 'danger' };
const STAGE_ICON: Record<CollectionStage, IconName> = { REMINDER: 'bell', SOFT: 'message', HARD: 'alert', LEGAL: 'scale' };
const ACTIONS: ActionType[] = ['CALL', 'SMS', 'VISIT', 'LETTER', 'LEGAL', 'NOTE'];
const CALL_RESULTS = ['PTP', 'CALLBACK', 'NO_ANSWER', 'WRONG_NUMBER', 'REFUSED'] as const;
const LEGAL_FLOW: LegalStatus[] = ['PRETRIAL', 'FILED', 'HEARING', 'DECIDED', 'ENFORCEMENT', 'CLOSED'];
const LEGAL_TONE: Record<LegalStatus, Tone> = {
  PRETRIAL: 'info', FILED: 'warn', HEARING: 'warn', DECIDED: 'danger', ENFORCEMENT: 'danger', CLOSED: 'neutral',
};
type Tab = 'all' | 'soft' | 'hard' | 'legal';

/** Natija kodlari tarjima qilinadi, qo'lda yozilgan matn o'zicha chiqadi. */
function useResultLabel() {
  const { t, i18n } = useTranslation();
  return (r?: string) => (r ? (i18n.exists(`res_${r}`) ? t(`res_${r}`) : r) : '');
}

export default function CollectionPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { stage: tabParam } = useParams();
  const branch = useBranch();
  const tab = (tabParam ?? 'all') as Tab;
  const { data, error } = useQuery({
    queryKey: ['overdue', branch.param],
    queryFn: () => api.get<OverdueRow[]>(`/api/collection/overdue${qs({ branchId: branch.param })}`),
  });
  const rows = data ?? [];
  const by = (stages: CollectionStage[]) => rows.filter((r) => stages.includes(r.stage));
  const total = rows.reduce((a, r) => a + r.overdueAmount, 0);

  if (!['all', 'soft', 'hard', 'legal'].includes(tab)) return <Navigate to="/collection" replace />;

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="collection" size={18} /></span>{t('collectionTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>
            {t('collectionSub')} · {t('overdue')}: <b className="mono">{fmtMln(total, lang)}</b> · {rows.length} {t('pcs')}
          </span>
        </div>
      </div>

      <Tabs items={[
        { key: 'all', to: '/collection', label: t('colOverview'), icon: 'dashboard', count: rows.length },
        { key: 'soft', to: '/collection/soft', label: t('soft'), icon: 'message', count: by(['REMINDER', 'SOFT']).length, tone: 'warn' },
        { key: 'hard', to: '/collection/hard', label: t('hard'), icon: 'alert', count: by(['HARD']).length, tone: 'danger' },
        { key: 'legal', to: '/collection/legal', label: t('legal'), icon: 'scale', count: by(['LEGAL']).length, tone: 'danger' },
      ]} />

      <ErrorBox error={error} />
      {tab === 'all' && <Overview rows={rows} loaded={!!data} />}
      {tab === 'soft' && <SoftView rows={by(['REMINDER', 'SOFT'])} loaded={!!data} />}
      {tab === 'hard' && <HardView rows={by(['HARD'])} loaded={!!data} />}
      {tab === 'legal' && <LegalView rows={by(['LEGAL'])} />}
    </div>
  );
}

/* ================================================================== umumiy ko'rinish */

function Overview({ rows, loaded }: { rows: OverdueRow[]; loaded: boolean }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const [stage, setStage] = useState<CollectionStage | ''>('');
  const [history, setHistory] = useState<OverdueRow | null>(null);
  const list = rows.filter((r) => !stage || r.stage === stage);
  return (
    <>
      <div className="funnel block">
        {STAGES.map((s, i) => {
          const l = rows.filter((r) => r.stage === s);
          return (
            <button key={s} className={`funnel-card fc-${s.toLowerCase()}`} data-on={stage === s}
              onClick={() => setStage(stage === s ? '' : s)}>
              <span className="funnel-step">0{i + 1}</span>
              <span className="row" style={{ gap: 6, fontSize: 12.5, color: 'var(--text-muted)' }}>
                <Icon name={STAGE_ICON[s]} size={15} />{t(STAGE_KEY[s])}
              </span>
              <span className="faint mono" style={{ fontSize: 10.5 }}>{t(`range_${s}`)}</span>
              <b className="mono" style={{ fontSize: 22, marginTop: 4 }}>{l.length}</b>
              <span className="mono muted" style={{ fontSize: 12 }}>{fmtMln(l.reduce((a, r) => a + r.overdueAmount, 0), lang)}</span>
            </button>
          );
        })}
      </div>
      {stage && (
        <div className={`stage-tip st-${STAGE_TONE[stage]}`}>
          <Icon name={STAGE_ICON[stage]} size={18} />
          <div className="col" style={{ gap: 2 }}>
            <b>{t(STAGE_KEY[stage])} · {t(`range_${stage}`)}</b>
            <span>{t(`tip_${stage}`)}</span>
          </div>
        </div>
      )}
      <Card className="block">
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('clientContract')}</th><th className="num">{t('overdue')}</th><th>{t('delay')}</th>
              <th>{t('status')}</th><th>{t('collector')}</th><th>{t('promise')}</th><th>{t('lastAction')}</th>
            </tr></thead>
            <tbody>
              {list.map((r) => (
                <tr key={r.contractId} onClick={() => setHistory(r)}>
                  <td className="cell-main"><ClientCell r={r} /></td>
                  <td className="num" data-label={t('overdue')}><b style={{ color: 'var(--danger)' }}>{fmtSom(r.overdueAmount, lang)}</b></td>
                  <td data-label={t('delay')}><span className="mono">{r.daysLate}</span> {t('daysLateShort')}</td>
                  <td data-label={t('status')}>
                    <Badge tone={STAGE_TONE[r.stage]} dot>{t(STAGE_KEY[r.stage])}</Badge>
                    {r.legalStatus && <span className="faint" style={{ fontSize: 11, marginLeft: 6 }}>{t(`ls_${r.legalStatus}`)}</span>}
                  </td>
                  <td data-label={t('collector')}>{r.collectorName ?? <span className="faint">—</span>}</td>
                  <td data-label={t('promise')}><PromiseBadge r={r} /></td>
                  <td data-label={t('lastAction')}>{r.lastActionDate ? fmtDate(r.lastActionDate, lang) : <span className="faint">—</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loaded && list.length === 0 && <Empty>{t('noOverdue')}</Empty>}
      </Card>
      {history && <ActionsModal row={history} onClose={() => setHistory(null)} />}
    </>
  );
}

/* ================================================================== soft */

function SoftView({ rows, loaded }: { rows: OverdueRow[]; loaded: boolean }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const [sel, setSel] = useState<Set<number>>(new Set());
  const [call, setCall] = useState<OverdueRow | null>(null);
  const [sms, setSms] = useState<OverdueRow[] | null>(null);
  const [history, setHistory] = useState<OverdueRow | null>(null);
  const today = todayIso();

  // Qo'ng'iroq navbati: buzilgan va'da → bugungi va'da/rejalashtirilgan → 3 kundan beri aloqa yo'q → qolganlari
  const queue = useMemo(() => {
    const prio = (r: OverdueRow) => {
      if (r.promiseStatus === 'BROKEN') return 0;
      if (r.promisedDate === today || r.plannedDate === today) return 1;
      if (!r.lastActionDate || daysSince(r.lastActionDate) >= 3) return 2;
      if (r.promiseStatus === 'PENDING') return 4;
      return 3;
    };
    return [...rows].sort((a, b) => prio(a) - prio(b) || b.daysLate - a.daysLate);
  }, [rows, today]);

  const broken = rows.filter((r) => r.promiseStatus === 'BROKEN').length;
  const pending = rows.filter((r) => r.promiseStatus === 'PENDING');
  const allSel = rows.length > 0 && sel.size === rows.length;
  const toggle = (id: number) => setSel((s) => {
    const n = new Set(s);
    if (n.has(id)) n.delete(id);
    else n.add(id);
    return n;
  });

  return (
    <>
      <div className="kpi4 block">
        <StageStat icon="message" tone="warn" label={t('softDebtors')} value={rows.length} sub={t('range_SOFT_ALL')} />
        <StageStat icon="wallet" tone="danger" label={t('overdue')} value={fmtMln(rows.reduce((a, r) => a + r.overdueAmount, 0), lang)}
          sub={`${t('remaining')}: ${fmtMln(rows.reduce((a, r) => a + r.remaining, 0), lang)}`} />
        <StageStat icon="calendar" tone="primary" label={t('promisesPending')} value={pending.length}
          sub={fmtMln(pending.reduce((a, r) => a + (r.promisedAmount ?? 0), 0), lang)} />
        <StageStat icon="alert" tone="gold" label={t('promisesBroken')} value={broken} sub={t('promisesBrokenSub')} />
      </div>

      <Card className="block">
        <div className="card-head">
          <div className="col" style={{ gap: 1 }}>
            <h3>{t('callQueue')}</h3>
            <span className="sub">{t('callQueueSub')}</span>
          </div>
          {can('COLLECTION_WORK') && (
            <div className="row" style={{ marginLeft: 'auto', gap: 8 }}>
              {sel.size > 0 && <span className="faint" style={{ fontSize: 12 }}>{t('selectedN', { n: sel.size })}</span>}
              <Btn size="sm" icon="send" disabled={sel.size === 0}
                onClick={() => setSms(rows.filter((r) => sel.has(r.contractId)))}>{t('bulkSms')}</Btn>
            </div>
          )}
        </div>
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              {can('COLLECTION_WORK') && <th style={{ width: 36 }}>
                <input type="checkbox" checked={allSel} aria-label={t('selectAll')}
                  onChange={() => setSel(allSel ? new Set() : new Set(rows.map((r) => r.contractId)))} />
              </th>}
              <th>{t('clientContract')}</th><th>{t('phone')}</th><th className="num">{t('overdue')}</th>
              <th>{t('delay')}</th><th>{t('promise')}</th><th>{t('lastAction')}</th><th>{t('quickActions')}</th>
            </tr></thead>
            <tbody>
              {queue.map((r) => (
                <tr key={r.contractId} onClick={() => setHistory(r)} data-hot={r.promiseStatus === 'BROKEN'}>
                  {can('COLLECTION_WORK') && <td onClick={(e) => e.stopPropagation()}>
                    <input type="checkbox" checked={sel.has(r.contractId)} onChange={() => toggle(r.contractId)} aria-label={r.clientName} />
                  </td>}
                  <td className="cell-main"><ClientCell r={r} /></td>
                  <td className="mono" data-label={t('phone')}>{r.phone}</td>
                  <td className="num" data-label={t('overdue')}><b style={{ color: 'var(--danger)' }}>{fmtSom(r.overdueAmount, lang)}</b></td>
                  <td data-label={t('delay')}><Badge tone={STAGE_TONE[r.stage]}>{r.daysLate} {t('daysLateShort')}</Badge></td>
                  <td data-label={t('promise')}><PromiseBadge r={r} /></td>
                  <td data-label={t('lastAction')}><LastAction r={r} /></td>
                  <td data-label={t('quickActions')} onClick={(e) => e.stopPropagation()}>
                    {can('COLLECTION_WORK') && (
                      <div className="row" style={{ gap: 4 }}>
                        <a className="act-btn" href={`tel:${r.phone}`} title={t('a_CALL')} onClick={() => setCall(r)}><Icon name="phone" size={15} /></a>
                        <button className="act-btn" title={t('callResult')} onClick={() => setCall(r)}><Icon name="userCheck" size={15} /></button>
                        <button className="act-btn" title={t('sendSms')} onClick={() => setSms([r])}><Icon name="message" size={15} /></button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loaded && rows.length === 0 && <Empty>{t('noOverdue')}</Empty>}
      </Card>
      {call && <CallModal row={call} onClose={() => setCall(null)} />}
      {sms && <SmsModal rows={sms} onClose={() => { setSms(null); setSel(new Set()); }} />}
      {history && <ActionsModal row={history} onClose={() => setHistory(null)} />}
    </>
  );
}

/* ================================================================== hard */

function HardView({ rows, loaded }: { rows: OverdueRow[]; loaded: boolean }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const qc = useQueryClient();
  const [visit, setVisit] = useState<OverdueRow | null>(null);
  const [history, setHistory] = useState<OverdueRow | null>(null);
  const [docErr, setDocErr] = useState<unknown>(null);
  const users = useQuery({ queryKey: ['users-brief'], queryFn: () => api.get<UserBrief[]>('/api/users/brief'), enabled: can('COLLECTION_ASSIGN') });
  const collectors = (users.data ?? []).filter((u) => u.active && (u.role === 'COLLECTOR' || u.role === 'ADMIN' || u.role === 'CREDIT_OFFICER'));
  const assign = useMutation({
    mutationFn: ({ id, userId }: { id: number; userId: number | null }) => api.put(`/api/collection/contracts/${id}/collector`, { userId }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['overdue'] }),
  });
  const demand = async (r: OverdueRow) => {
    setDocErr(null);
    try {
      await openPdf('POST', `/api/documents/contracts/${r.contractId}/demand?lang=${lang === 'ru' ? 'ru' : 'uz'}`);
      void qc.invalidateQueries({ queryKey: ['overdue'] });
    } catch (e) {
      setDocErr(e);
    }
  };
  const lettered = rows.filter((r) => r.lettersSent > 0).length;
  const visits = rows.filter((r) => r.plannedType === 'VISIT').length;

  return (
    <>
      <div className="kpi4 block">
        <StageStat icon="alert" tone="danger" label={t('hardContracts')} value={rows.length} sub={t('range_HARD')} />
        <StageStat icon="wallet" tone="gold" label={t('atRiskDebt')} value={fmtMln(rows.reduce((a, r) => a + r.remaining, 0), lang)}
          sub={`${t('overdue')}: ${fmtMln(rows.reduce((a, r) => a + r.overdueAmount, 0), lang)}`} />
        <StageStat icon="doc" tone="primary" label={t('lettersSent')} value={`${lettered} / ${rows.length}`} sub={t('lettersSentSub')} />
        <StageStat icon="pin" tone="info" label={t('visitsPlanned')} value={visits} sub={t('visitsPlannedSub')} />
      </div>
      <div className="stage-tip st-danger"><Icon name="alert" size={18} /><span>{t('tip_HARD')} {t('noPenaltyNote')}</span></div>
      <ErrorBox error={docErr ?? assign.error} />
      <Card className="block">
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('clientContract')}</th><th className="num">{t('overdue')}</th><th className="num">{t('remaining')}</th>
              <th>{t('delay')}</th><th>{t('collector')}</th><th>{t('docDemandShort')}</th><th>{t('visit')}</th><th>{t('quickActions')}</th>
            </tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.contractId} onClick={() => setHistory(r)}>
                  <td className="cell-main"><ClientCell r={r} /></td>
                  <td className="num" data-label={t('overdue')}><b style={{ color: 'var(--danger)' }}>{fmtSom(r.overdueAmount, lang)}</b></td>
                  <td className="num mono" data-label={t('remaining')}>{fmtMln(r.remaining, lang)}</td>
                  <td data-label={t('delay')}><span className="mono">{r.daysLate}</span> {t('daysLateShort')}</td>
                  <td data-label={t('collector')} onClick={(e) => e.stopPropagation()}>
                    {can('COLLECTION_ASSIGN') ? (
                      <select className="select select-sm" value={r.collectorId ?? ''} disabled={assign.isPending}
                        onChange={(e) => assign.mutate({ id: r.contractId, userId: e.target.value ? Number(e.target.value) : null })}>
                        <option value="">{t('notAssigned')}</option>
                        {collectors.map((u) => <option key={u.id} value={u.id}>{u.fullName}</option>)}
                      </select>
                    ) : (r.collectorName ?? <span className="faint">—</span>)}
                  </td>
                  <td data-label={t('docDemandShort')}>
                    {r.lettersSent > 0
                      ? <Badge tone="success"><Icon name="check" size={12} /> {r.lettersSent} · {fmtDate(r.lastLetterDate, lang)}</Badge>
                      : <Badge tone="warn">{t('notSent')}</Badge>}
                  </td>
                  <td data-label={t('visit')}>
                    {r.plannedType === 'VISIT' && r.plannedDate ? <Badge tone="info"><Icon name="pin" size={12} /> {fmtDate(r.plannedDate, lang)}</Badge> : <span className="faint">—</span>}
                  </td>
                  <td data-label={t('quickActions')} onClick={(e) => e.stopPropagation()}>
                    {can('COLLECTION_WORK') && (
                      <div className="row" style={{ gap: 4 }}>
                        <button className="act-btn" title={t('docDemand')} onClick={() => void demand(r)}><Icon name="printer" size={15} /></button>
                        <button className="act-btn" title={t('planVisit')} onClick={() => setVisit(r)}><Icon name="pin" size={15} /></button>
                        <a className="act-btn" href={`tel:${r.phone}`} title={t('a_CALL')}><Icon name="phone" size={15} /></a>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {loaded && rows.length === 0 && <Empty>{t('noOverdue')}</Empty>}
      </Card>
      {visit && <VisitModal row={visit} onClose={() => setVisit(null)} />}
      {history && <ActionsModal row={history} onClose={() => setHistory(null)} />}
    </>
  );
}

/* ================================================================== legal */

function LegalView({ rows }: { rows: OverdueRow[] }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const branch = useBranch();
  const qc = useQueryClient();
  const [showClosed, setShowClosed] = useState(false);
  const [edit, setEdit] = useState<LegalCaseRow | null>(null);
  const [docErr, setDocErr] = useState<unknown>(null);
  const cases = useQuery({
    queryKey: ['legal', showClosed, branch.param],
    queryFn: () => api.get<LegalCaseRow[]>(`/api/collection/legal${qs({ includeClosed: showClosed, branchId: branch.param })}`),
  });
  const openCase = useMutation({
    mutationFn: (contractId: number) => api.post('/api/collection/legal', { contractId }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['legal'] });
      void qc.invalidateQueries({ queryKey: ['overdue'] });
    },
  });
  const claim = async (contractId: number) => {
    setDocErr(null);
    try {
      await openPdf('POST', `/api/documents/contracts/${contractId}/claim?lang=${lang === 'ru' ? 'ru' : 'uz'}`);
    } catch (e) {
      setDocErr(e);
    }
  };
  const legalPackage = async (contractId: number) => {
    setDocErr(null);
    try {
      await downloadFile('POST', `/api/documents/contracts/${contractId}/legal-package?lang=${lang === 'ru' ? 'ru' : 'uz'}`, `legal-package-${contractId}.zip`);
    } catch (e) {
      setDocErr(e);
    }
  };
  const list = cases.data ?? [];
  const candidates = rows.filter((r) => !r.legalCaseId);
  const openList = list.filter((c) => c.status !== 'CLOSED');

  return (
    <>
      <div className="legal-flow block">
        {LEGAL_FLOW.map((s, i) => {
          const n = list.filter((c) => c.status === s).length;
          return (
            <div key={s} className={`lf-step lf-${LEGAL_TONE[s]}`} data-on={n > 0}>
              <span className="lf-n mono">{n}</span>
              <span className="lf-l">{t(`ls_${s}`)}</span>
              {i < LEGAL_FLOW.length - 1 && <Icon name="chevR" size={14} style={{ position: 'absolute', right: -11, top: '50%', marginTop: -7, color: 'var(--text-dim)' }} />}
            </div>
          );
        })}
      </div>

      <div className="kpi4 block">
        <StageStat icon="scale" tone="danger" label={t('legalOpen')} value={openList.length} sub={t('legalOpenSub')} />
        <StageStat icon="wallet" tone="gold" label={t('claimTotal')} value={fmtMln(openList.reduce((a, c) => a + c.claimAmount, 0), lang)} sub={t('claimTotalSub')} />
        <StageStat icon="trend" tone="success" label={t('paidSinceCase')} value={fmtMln(list.reduce((a, c) => a + c.paidSinceOpened, 0), lang)} sub={t('paidSinceCaseSub')} />
        <StageStat icon="alert" tone="primary" label={t('legalCandidates')} value={candidates.length} sub={t('range_LEGAL')} />
      </div>
      <div className="stage-tip st-info"><Icon name="shieldCheck" size={18} /><span>{t('legalNoPenalty')}</span></div>
      <ErrorBox error={docErr ?? openCase.error} />

      <Card className="block">
        <div className="card-head">
          <div className="col" style={{ gap: 1 }}><h3>{t('legalCases')}</h3><span className="sub">{t('legalCasesSub')}</span></div>
          <label className="check-row" style={{ marginLeft: 'auto', fontSize: 12.5 }}>
            <input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} />{t('showClosed')}
          </label>
        </div>
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('clientContract')}</th><th>{t('status')}</th><th className="num">{t('claimAmount')}</th>
              <th className="num">{t('remaining')}</th><th>{t('court')}</th><th>{t('dates')}</th><th>{t('quickActions')}</th>
            </tr></thead>
            <tbody>
              {list.map((c) => (
                <tr key={c.id} onClick={() => can('COLLECTION_LEGAL') && setEdit(c)}>
                  <td className="cell-main">
                    <div className="col" style={{ gap: 1 }}>
                      <Link to={`/clients/${c.clientId}`} onClick={(e) => e.stopPropagation()}><b>{c.clientName}</b></Link>
                      <span className="mono faint" style={{ fontSize: 11 }}>{c.contractNo} · {c.pinfl}</span>
                    </div>
                  </td>
                  <td data-label={t('status')}><Badge tone={LEGAL_TONE[c.status]} dot>{t(`ls_${c.status}`)}</Badge></td>
                  <td className="num mono" data-label={t('claimAmount')}>{fmtSom(c.claimAmount, lang)}</td>
                  <td className="num mono" data-label={t('remaining')}>
                    {fmtSom(c.remaining, lang)}
                    {c.paidSinceOpened > 0 && <div style={{ fontSize: 11, color: 'var(--success)' }}>+{fmtMln(c.paidSinceOpened, lang)}</div>}
                  </td>
                  <td data-label={t('court')}>
                    <div className="col" style={{ gap: 1 }}>
                      <span>{c.courtName ?? <span className="faint">—</span>}</span>
                      {c.caseNo && <span className="mono faint" style={{ fontSize: 11 }}>№ {c.caseNo}</span>}
                    </div>
                  </td>
                  <td data-label={t('dates')} style={{ fontSize: 12 }}>
                    {c.hearingAt ? <>{t('hearing')}: <b>{fmtDate(c.hearingAt, lang)}</b></>
                      : c.filedAt ? <>{t('filed')}: {fmtDate(c.filedAt, lang)}</> : fmtDate(c.createdAt, lang)}
                  </td>
                  <td data-label={t('quickActions')} onClick={(e) => e.stopPropagation()}>
                    {can('COLLECTION_LEGAL') && (
                      <div className="row" style={{ gap: 4 }}>
                        <button className="act-btn" title={t('docClaim')} onClick={() => void claim(c.contractId)}><Icon name="printer" size={15} /></button>
                        <button className="act-btn" title={t('legalPackage')} onClick={() => void legalPackage(c.contractId)}><Icon name="download" size={15} /></button>
                        <button className="act-btn" title={t('edit')} onClick={() => setEdit(c)}><Icon name="pen" size={15} /></button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {cases.data && list.length === 0 && <Empty>{t('noLegalCases')}</Empty>}
      </Card>

      {candidates.length > 0 && (
        <Card className="block">
          <div className="card-head">
            <div className="col" style={{ gap: 1 }}><h3>{t('legalCandidates')}</h3><span className="sub">{t('legalCandidatesSub')}</span></div>
          </div>
          <div className="table-wrap">
            <table className="tbl">
              <thead><tr><th>{t('clientContract')}</th><th className="num">{t('remaining')}</th><th>{t('delay')}</th><th>{t('docDemandShort')}</th><th /></tr></thead>
              <tbody>
                {candidates.map((r) => (
                  <tr key={r.contractId} style={{ cursor: 'default' }}>
                    <td className="cell-main"><ClientCell r={r} /></td>
                    <td className="num mono" data-label={t('remaining')}>{fmtSom(r.remaining, lang)}</td>
                    <td data-label={t('delay')}><Badge tone="danger">{r.daysLate} {t('daysLateShort')}</Badge></td>
                    <td data-label={t('docDemandShort')}>{r.lettersSent > 0 ? <Badge tone="success">{fmtDate(r.lastLetterDate, lang)}</Badge> : <Badge tone="warn">{t('notSent')}</Badge>}</td>
                    <td>{can('COLLECTION_LEGAL') && (
                      <Btn size="sm" variant="ghost" icon="scale" disabled={openCase.isPending}
                        onClick={() => openCase.mutate(r.contractId)}>{t('openCase')}</Btn>
                    )}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
      {edit && <LegalModal row={edit} onClose={() => setEdit(null)} />}
    </>
  );
}

/* ================================================================== umumiy bo'laklar */

function StageStat({ icon, tone, label, value, sub }: { icon: IconName; tone: string; label: string; value: ReactNode; sub?: ReactNode }) {
  return (
    <div className="stat anim" data-tone={tone}>
      <span className="stat-ic"><Icon name={icon} /></span>
      <span className="stat-label">{label}</span>
      <div className="stat-val">{value}</div>
      {sub && <span className="stat-sub">{sub}</span>}
    </div>
  );
}

function ClientCell({ r }: { r: OverdueRow }) {
  return (
    <div className="col" style={{ gap: 1 }}>
      <Link to={`/clients/${r.clientId}`} onClick={(e) => e.stopPropagation()}><b>{r.clientName}</b></Link>
      <span className="mono faint" style={{ fontSize: 11 }}>
        <Link to={`/contracts/${r.contractId}`} onClick={(e) => e.stopPropagation()}>{r.contractNo}</Link>
        {r.branchName ? ` · ${r.branchName}` : ''}
      </span>
    </div>
  );
}

function PromiseBadge({ r }: { r: OverdueRow }) {
  const { t, i18n } = useTranslation();
  if (!r.promiseStatus || !r.promisedDate) return <span className="faint">—</span>;
  const tone: Tone = r.promiseStatus === 'KEPT' ? 'success' : r.promiseStatus === 'BROKEN' ? 'danger' : 'info';
  return (
    <div className="col" style={{ gap: 2 }}>
      <Badge tone={tone} dot>{t(`ps_${r.promiseStatus}`)}</Badge>
      <span className="faint mono" style={{ fontSize: 11 }}>
        {fmtDate(r.promisedDate, i18n.language)}{r.promisedAmount ? ` · ${fmtMln(r.promisedAmount, i18n.language)}` : ''}
      </span>
    </div>
  );
}

function LastAction({ r }: { r: OverdueRow }) {
  const { t, i18n } = useTranslation();
  const label = useResultLabel();
  if (!r.lastActionDate) return <span className="faint">{t('noContact')}</span>;
  return (
    <div className="col" style={{ gap: 1 }}>
      <span style={{ fontSize: 12.5 }}>{r.lastActionType ? t(`a_${r.lastActionType}`) : ''}{r.lastResult ? ` · ${label(r.lastResult)}` : ''}</span>
      <span className="faint mono" style={{ fontSize: 11 }}>{fmtDate(r.lastActionDate, i18n.language)}</span>
    </div>
  );
}

function useInvalidate(contractId?: number) {
  const qc = useQueryClient();
  return () => {
    void qc.invalidateQueries({ queryKey: ['overdue'] });
    if (contractId) void qc.invalidateQueries({ queryKey: ['actions', contractId] });
  };
}

function CallModal({ row, onClose }: { row: OverdueRow; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const [result, setResult] = useState<(typeof CALL_RESULTS)[number]>('PTP');
  const [date, setDate] = useState('');
  const [amount, setAmount] = useState(String(Math.round(row.overdueAmount)));
  const [note, setNote] = useState('');
  const done = useInvalidate(row.contractId);
  const save = useMutation({
    mutationFn: () => api.post(`/api/collection/contracts/${row.contractId}/actions`, {
      actionType: 'CALL', result, note: note || undefined,
      promisedDate: result === 'PTP' && date ? date : undefined,
      promisedAmount: result === 'PTP' && amount ? Number(amount) : undefined,
      plannedDate: result === 'CALLBACK' && date ? date : undefined,
    }),
    onSuccess: () => { done(); onClose(); },
  });
  const needDate = result === 'PTP' || result === 'CALLBACK';
  return (
    <Modal title={`${t('callResult')} · ${row.clientName}`} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending || (needDate && !date)} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <div className="call-head">
          <a className="btn btn-soft" href={`tel:${row.phone}`}><Icon name="phone" size={16} />{row.phone}</a>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('overdue')}: <b style={{ color: 'var(--danger)' }}>{fmtSom(row.overdueAmount, i18n.language)}</b> · {row.daysLate} {t('daysLateShort')}</span>
        </div>
        <div className="result-grid">
          {CALL_RESULTS.map((r) => (
            <button key={r} type="button" className="result-btn" data-on={result === r} onClick={() => setResult(r)}>
              {t(`res_${r}`)}
            </button>
          ))}
        </div>
        {needDate && (
          <div className="form-grid">
            <Field label={result === 'PTP' ? t('promisedDate') : t('callbackDate')} req>
              <input className="input" type="date" min={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} />
            </Field>
            {result === 'PTP' && (
              <Field label={t('promisedAmount')}>
                <input className="input mono" type="number" min={0} value={amount} onChange={(e) => setAmount(e.target.value)} />
              </Field>
            )}
          </div>
        )}
        <Field label={t('note')}><textarea className="input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        <ErrorBox error={save.error} />
      </div>
    </Modal>
  );
}

const SMS_TEMPLATES = ['smsTplSoft', 'smsTplFirm', 'smsTplPromise'] as const;

function SmsModal({ rows, onClose }: { rows: OverdueRow[]; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language === 'ru' ? 'ru' : 'uz';
  const [tpl, setTpl] = useState<(typeof SMS_TEMPLATES)[number]>('smsTplSoft');
  const [text, setText] = useState(() => t('smsTplSoft'));
  const done = useInvalidate(rows.length === 1 ? rows[0].contractId : undefined);
  const send = useMutation({
    mutationFn: () => api.post<{ total: number; byStatus: Partial<Record<'SENT' | 'FAILED' | 'SKIPPED', number>> }>(
      '/api/collection/sms/bulk', { contractIds: rows.map((r) => r.contractId), text, lang }),
    onSuccess: done,
  });
  const first = rows[0];
  const preview = text.replace('{ism}', first.clientName).replace('{summa}', fmtSom(first.overdueAmount, lang))
    .replace('{shartnoma}', first.contractNo).replace('{kun}', String(first.daysLate));
  const r = send.data;
  return (
    <Modal wide title={rows.length === 1 ? `${t('sendSms')} · ${first.phone}` : t('bulkSmsTitle', { n: rows.length })} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('close')}</Btn>
        {!r && <Btn icon="send" disabled={send.isPending || !text.trim()} onClick={() => send.mutate()}>{t('send')}</Btn>}
      </>}>
      <div className="col" style={{ gap: 12 }}>
        <div className="seg" style={{ alignSelf: 'flex-start', flexWrap: 'wrap' }}>
          {SMS_TEMPLATES.map((k) => (
            <button key={k} type="button" data-on={tpl === k} disabled={!!r} onClick={() => { setTpl(k); setText(t(k)); }}>{t(`${k}Name`)}</button>
          ))}
        </div>
        <Field label={t('smsText')} hint={`${text.length} / 500 · {ism} {summa} {shartnoma} {kun}`}>
          <textarea className="input" rows={4} maxLength={500} value={text} onChange={(e) => setText(e.target.value)} disabled={!!r} />
        </Field>
        <div className="sms-preview"><span className="faint" style={{ fontSize: 11 }}>{t('preview')} · {first.clientName}</span>{preview}</div>
        <ErrorBox error={send.error} />
        {r && (
          <div className="receipt-ok"><Icon name="check" size={16} />
            {t('bulkSmsResult', { sent: r.byStatus.SENT ?? 0, skipped: r.byStatus.SKIPPED ?? 0, failed: r.byStatus.FAILED ?? 0 })}
          </div>
        )}
      </div>
    </Modal>
  );
}

function VisitModal({ row, onClose }: { row: OverdueRow; onClose: () => void }) {
  const { t } = useTranslation();
  const [date, setDate] = useState('');
  const [note, setNote] = useState('');
  const done = useInvalidate(row.contractId);
  const save = useMutation({
    mutationFn: () => api.post(`/api/collection/contracts/${row.contractId}/actions`, {
      actionType: 'VISIT', result: 'PLANNED', plannedDate: date, note: note || undefined,
    }),
    onSuccess: () => { done(); onClose(); },
  });
  return (
    <Modal title={`${t('planVisit')} · ${row.clientName}`} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={!date || save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <Field label={t('visitDate')} req><input className="input" type="date" min={todayIso()} value={date} onChange={(e) => setDate(e.target.value)} /></Field>
        <Field label={t('note')} hint={t('visitNoteHint')}><textarea className="input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        <ErrorBox error={save.error} />
      </div>
    </Modal>
  );
}

function LegalModal({ row, onClose }: { row: LegalCaseRow; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [d, setD] = useState({
    status: row.status, courtName: row.courtName ?? '', caseNo: row.caseNo ?? '', filedAt: row.filedAt ?? '',
    hearingAt: row.hearingAt ?? '', decisionAt: row.decisionAt ?? '', enforcementNo: row.enforcementNo ?? '', note: row.note ?? '',
  });
  const save = useMutation({
    mutationFn: () => api.put(`/api/collection/legal/${row.id}`, {
      ...d, assignedUserId: row.assignedUserId,
      filedAt: d.filedAt || null, hearingAt: d.hearingAt || null, decisionAt: d.decisionAt || null,
    }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['legal'] });
      void qc.invalidateQueries({ queryKey: ['overdue'] });
      onClose();
    },
  });
  const set = (k: keyof typeof d, v: string) => setD({ ...d, [k]: v });
  return (
    <Modal wide title={`${row.clientName} · ${row.contractNo}`} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <div className="legal-steps">
          {LEGAL_FLOW.map((s) => (
            <button key={s} type="button" className="legal-step" data-on={d.status === s}
              data-done={LEGAL_FLOW.indexOf(s) < LEGAL_FLOW.indexOf(d.status)} onClick={() => set('status', s)}>
              {t(`ls_${s}`)}
            </button>
          ))}
        </div>
        <div className="well-grid" style={{ gridTemplateColumns: 'repeat(3, minmax(0, 1fr))' }}>
          <div className="well"><span className="well-label">{t('claimAmount')}</span><b className="well-val">{fmtMln(row.claimAmount, i18n.language)}</b></div>
          <div className="well"><span className="well-label">{t('remaining')}</span><b className="well-val">{fmtMln(row.remaining, i18n.language)}</b></div>
          <div className="well"><span className="well-label">{t('paidSinceCase')}</span><b className="well-val" style={{ color: 'var(--success)' }}>{fmtMln(row.paidSinceOpened, i18n.language)}</b></div>
        </div>
        <div className="form-grid">
          <Field label={t('court')}><input className="input" value={d.courtName} onChange={(e) => set('courtName', e.target.value)} /></Field>
          <Field label={t('caseNo')}><input className="input mono" value={d.caseNo} onChange={(e) => set('caseNo', e.target.value)} /></Field>
          <Field label={t('filed')}><input className="input" type="date" value={d.filedAt} onChange={(e) => set('filedAt', e.target.value)} /></Field>
          <Field label={t('hearing')}><input className="input" type="date" value={d.hearingAt} onChange={(e) => set('hearingAt', e.target.value)} /></Field>
          <Field label={t('decision')}><input className="input" type="date" value={d.decisionAt} onChange={(e) => set('decisionAt', e.target.value)} /></Field>
          <Field label={t('enforcementNo')}><input className="input mono" value={d.enforcementNo} onChange={(e) => set('enforcementNo', e.target.value)} /></Field>
        </div>
        <Field label={t('note')}><textarea className="input" rows={2} value={d.note} onChange={(e) => set('note', e.target.value)} /></Field>
        {row.status === 'PRETRIAL' && d.status === 'FILED' && <div className="stage-tip st-info"><Icon name="spark" size={16} /><span>{t('claimRecalc')}</span></div>}
        <ErrorBox error={save.error} />
      </div>
    </Modal>
  );
}

function ActionsModal({ row, onClose }: { row: OverdueRow; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const { can } = useAuth();
  const label = useResultLabel();
  const [actionType, setActionType] = useState<ActionType>('CALL');
  const [result, setResult] = useState('');
  const [note, setNote] = useState('');
  const [promisedDate, setPromisedDate] = useState('');
  const [promisedAmount, setPromisedAmount] = useState('');
  const done = useInvalidate(row.contractId);
  const history = useQuery({
    queryKey: ['actions', row.contractId],
    queryFn: () => api.get<CollectionActionDto[]>(`/api/collection/contracts/${row.contractId}/actions`),
  });
  const add = useMutation({
    mutationFn: () => api.post(`/api/collection/contracts/${row.contractId}/actions`, {
      actionType, result: result || undefined, note: note || undefined,
      promisedDate: promisedDate || undefined, promisedAmount: promisedAmount ? Number(promisedAmount) : undefined,
    }),
    onSuccess: () => {
      done();
      setNote('');
      setResult('');
    },
  });
  return (
    <Modal wide title={`${row.clientName} · ${row.contractNo}`} onClose={onClose}
      footer={can('COLLECTION_WORK') ? <Btn icon="plus" disabled={add.isPending} onClick={() => add.mutate()}>{t('addAction')}</Btn> : undefined}>
      <div className="col" style={{ gap: 14 }}>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
          <Badge tone={STAGE_TONE[row.stage]} dot>{t(STAGE_KEY[row.stage])}</Badge>
          <Badge tone="danger">{t('overdue')}: {fmtSom(row.overdueAmount, i18n.language)}</Badge>
          <Badge tone="neutral">{t('remaining')}: {fmtSom(row.remaining, i18n.language)}</Badge>
          {row.collectorName && <Badge tone="info"><Icon name="user" size={12} /> {row.collectorName}</Badge>}
        </div>
        {can('COLLECTION_WORK') && (
          <>
            <ErrorBox error={add.error} />
            <div className="form-grid">
              <Field label={t('event')}>
                <select className="select" value={actionType} onChange={(e) => setActionType(e.target.value as ActionType)}>
                  {ACTIONS.map((a) => <option key={a} value={a}>{t(`a_${a}`)}</option>)}
                </select>
              </Field>
              <Field label={t('result')}><input className="input" value={result} maxLength={30} onChange={(e) => setResult(e.target.value)} /></Field>
              <Field label={t('promisedDate')}><input className="input" type="date" value={promisedDate} onChange={(e) => setPromisedDate(e.target.value)} /></Field>
              <Field label={t('promisedAmount')}><input className="input" type="number" value={promisedAmount} onChange={(e) => setPromisedAmount(e.target.value)} /></Field>
            </div>
            <Field label={t('note')}><textarea className="input" rows={2} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
            <div className="divider" />
          </>
        )}
        <b style={{ fontSize: 13 }}>{t('history')}</b>
        <div className="timeline">
          {history.data?.map((a) => (
            <div key={a.id} className="tl-item">
              <span className={`tl-dot tl-${a.actionType}`} />
              <div className="col" style={{ gap: 2, minWidth: 0 }}>
                <span><b>{t(`a_${a.actionType}`)}</b>{a.result ? ` · ${label(a.result)}` : ''}</span>
                {a.note && <span className="muted" style={{ fontSize: 12.5 }}>{a.note}</span>}
                <span className="faint" style={{ fontSize: 11.5 }}>
                  {fmtDate(a.createdAt, i18n.language)}{a.userName ? ` · ${a.userName}` : ''}
                  {a.promisedDate && ` · ${t('promisedDate')}: ${fmtDate(a.promisedDate, i18n.language)}`}
                  {a.plannedDate && ` · ${t('planned')}: ${fmtDate(a.plannedDate, i18n.language)}`}
                </span>
              </div>
            </div>
          ))}
        </div>
        {history.data?.length === 0 && <Empty />}
      </div>
    </Modal>
  );
}
