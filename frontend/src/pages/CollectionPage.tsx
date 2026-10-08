import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type {
  ActionType, ClientDetails, CollectionActionDto, CollectionStage, ContractDetails, OverdueRow, Settings,
} from '../api/types';
import { Badge, Btn, Card, Empty, ErrorBox, Field, Icon, Modal, type Tone } from '../components/ui';
import type { IconName } from '../components/icons';
import { fmtDate, fmtMln, fmtSom, todayIso } from '../lib/format';

const STAGES: CollectionStage[] = ['REMINDER', 'SOFT', 'HARD', 'LEGAL'];
const STAGE_KEY: Record<CollectionStage, string> = { REMINDER: 'reminder', SOFT: 'soft', HARD: 'hard', LEGAL: 'legal' };
const STAGE_TONE: Record<CollectionStage, Tone> = { REMINDER: 'info', SOFT: 'warn', HARD: 'danger', LEGAL: 'danger' };
const STAGE_ICON: Record<CollectionStage, IconName> = { REMINDER: 'bell', SOFT: 'message', HARD: 'alert', LEGAL: 'scale' };
const ACTIONS: ActionType[] = ['CALL', 'SMS', 'VISIT', 'LETTER', 'LEGAL', 'NOTE'];

type Doc = 'demand' | 'claim';

export default function CollectionPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const [stage, setStage] = useState<CollectionStage | ''>('');
  const [history, setHistory] = useState<{ row: OverdueRow; type?: ActionType } | null>(null);
  const [sms, setSms] = useState<OverdueRow | null>(null);
  const [doc, setDoc] = useState<{ row: OverdueRow; kind: Doc } | null>(null);
  const { data, error } = useQuery({ queryKey: ['overdue'], queryFn: () => api.get<OverdueRow[]>('/api/collection/overdue') });

  const rows = (data ?? []).filter((r) => !stage || r.stage === stage);
  const total = (data ?? []).reduce((a, r) => a + r.overdueAmount, 0);

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="collection" size={18} /></span>{t('collectionTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>
            {t('collectionSub')} · {t('overdue')}: <b className="mono">{fmtMln(total, lang)}</b> · {data?.length ?? 0} {t('pcs')}
          </span>
        </div>
        {stage && <Btn variant="ghost" size="sm" onClick={() => setStage('')}>{t('allStages')}</Btn>}
      </div>

      <div className="funnel">
        {STAGES.map((s, i) => {
          const list = data?.filter((r) => r.stage === s) ?? [];
          return (
            <button key={s} className={`funnel-card fc-${s.toLowerCase()}`} data-on={stage === s}
              onClick={() => setStage(stage === s ? '' : s)}>
              <span className="funnel-step">0{i + 1}</span>
              <span className="row" style={{ gap: 6, fontSize: 12.5, color: 'var(--text-muted)' }}>
                <Icon name={STAGE_ICON[s]} size={15} />{t(STAGE_KEY[s])}
              </span>
              <span className="faint mono" style={{ fontSize: 10.5 }}>{t(`range_${s}`)}</span>
              <b className="mono" style={{ fontSize: 22, marginTop: 4 }}>{list.length}</b>
              <span className="mono muted" style={{ fontSize: 12 }}>{fmtMln(list.reduce((a, r) => a + r.overdueAmount, 0), lang)}</span>
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

      <ErrorBox error={error} />
      <Card className="block">
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('clientContract')}</th><th>{t('phone')}</th>
              <th className="num">{t('overdue')}</th><th>{t('daysLate')}</th><th>{t('status')}</th>
              <th>{t('lastAction')}</th><th>{t('quickActions')}</th>
            </tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.contractId} onClick={() => setHistory({ row: r })}>
                  <td className="cell-main">
                    <div className="col" style={{ gap: 1 }}>
                      <Link to={`/clients/${r.clientId}`} onClick={(e) => e.stopPropagation()}><b>{r.clientName}</b></Link>
                      <Link to={`/contracts/${r.contractId}`} className="mono faint" style={{ fontSize: 11 }}
                        onClick={(e) => e.stopPropagation()}>{r.contractNo}</Link>
                    </div>
                  </td>
                  <td className="mono" data-label={t('phone')}>{r.phone}</td>
                  <td className="num" data-label={t('overdue')}><b style={{ color: 'var(--danger)' }}>{fmtSom(r.overdueAmount, lang)}</b></td>
                  <td data-label={t('daysLate')}><span className="mono">{r.daysLate}</span> {t('daysLateShort')}</td>
                  <td data-label={t('status')}><Badge tone={STAGE_TONE[r.stage]} dot>{t(STAGE_KEY[r.stage])}</Badge></td>
                  <td data-label={t('lastAction')}>{r.lastActionDate ? fmtDate(r.lastActionDate, lang) : <span className="faint">—</span>}</td>
                  <td data-label={t('quickActions')} onClick={(e) => e.stopPropagation()}>
                    <div className="row" style={{ gap: 4 }}>
                      <a className="act-btn" href={`tel:${r.phone}`} title={t('a_CALL')}
                        onClick={() => setHistory({ row: r, type: 'CALL' })}><Icon name="phone" size={15} /></a>
                      <button className="act-btn" title={t('sendSms')} onClick={() => setSms(r)}><Icon name="message" size={15} /></button>
                      {(r.stage === 'HARD' || r.stage === 'LEGAL') && (
                        <button className="act-btn" title={t('docDemand')} onClick={() => setDoc({ row: r, kind: 'demand' })}><Icon name="doc" size={15} /></button>
                      )}
                      {r.stage === 'LEGAL' && (
                        <button className="act-btn" title={t('docClaim')} onClick={() => setDoc({ row: r, kind: 'claim' })}><Icon name="scale" size={15} /></button>
                      )}
                      <button className="act-btn" title={t('history')} onClick={() => setHistory({ row: r })}><Icon name="clock" size={15} /></button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && rows.length === 0 && <Empty>{t('noOverdue')}</Empty>}
      </Card>
      {history && <ActionsModal row={history.row} initialType={history.type} onClose={() => setHistory(null)} />}
      {sms && <SmsModal row={sms} onClose={() => setSms(null)} />}
      {doc && <DocModal row={doc.row} kind={doc.kind} onClose={() => setDoc(null)} />}
    </div>
  );
}

function ActionsModal({ row, initialType, onClose }: { row: OverdueRow; initialType?: ActionType; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [actionType, setActionType] = useState<ActionType>(initialType ?? 'CALL');
  const [result, setResult] = useState('');
  const [note, setNote] = useState('');
  const [promisedDate, setPromisedDate] = useState('');
  const [promisedAmount, setPromisedAmount] = useState('');
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
      void qc.invalidateQueries({ queryKey: ['actions', row.contractId] });
      void qc.invalidateQueries({ queryKey: ['overdue'] });
      setNote('');
      setResult('');
    },
  });
  return (
    <Modal wide title={`${row.clientName} · ${row.contractNo}`} onClose={onClose}
      footer={<Btn icon="plus" disabled={add.isPending} onClick={() => add.mutate()}>{t('addAction')}</Btn>}>
      <div className="col" style={{ gap: 14 }}>
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
        <b style={{ fontSize: 13 }}>{t('history')}</b>
        {history.data?.map((a) => (
          <div key={a.id} className="row" style={{ gap: 10, alignItems: 'flex-start' }}>
            <Badge tone="neutral">{t(`a_${a.actionType}`)}</Badge>
            <div className="col" style={{ gap: 2 }}>
              <span>{[a.result, a.note].filter(Boolean).join(' — ')}</span>
              <span className="faint" style={{ fontSize: 12 }}>
                {fmtDate(a.createdAt, i18n.language)}
                {a.promisedDate && ` · ${t('promisedDate')}: ${fmtDate(a.promisedDate, i18n.language)}`}
              </span>
            </div>
          </div>
        ))}
        {history.data?.length === 0 && <Empty />}
      </div>
    </Modal>
  );
}

function SmsModal({ row, onClose }: { row: OverdueRow; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [text, setText] = useState(() => t('smsDefault', {
    name: row.clientName.split(' ')[0], no: row.contractNo, sum: fmtSom(row.overdueAmount, i18n.language),
  }));
  const send = useMutation({
    mutationFn: () => api.post<{ status: 'SENT' | 'FAILED' | 'SKIPPED' }>(`/api/collection/contracts/${row.contractId}/sms`, { text }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['overdue'] });
      void qc.invalidateQueries({ queryKey: ['actions', row.contractId] });
    },
  });
  const status = send.data?.status;
  return (
    <Modal title={`${t('sendSms')} · ${row.phone}`} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('close')}</Btn>
        {!status && <Btn icon="message" disabled={send.isPending || !text.trim()} onClick={() => send.mutate()}>{t('send')}</Btn>}
      </>}>
      <div className="col" style={{ gap: 12 }}>
        <Field label={t('smsText')} hint={`${text.length} / 500`}>
          <textarea className="input" rows={4} maxLength={500} value={text} onChange={(e) => setText(e.target.value)} disabled={!!status} />
        </Field>
        <ErrorBox error={send.error} />
        {status === 'SENT' && <div className="receipt-ok"><Icon name="check" size={16} />{t('smsSent')}</div>}
        {status === 'SKIPPED' && <div className="stage-tip st-warn"><Icon name="alert" size={16} /><span>{t('smsSkipped')}</span></div>}
        {status === 'FAILED' && <div className="stage-tip st-danger"><Icon name="alert" size={16} /><span>{t('smsFailed')}</span></div>}
      </div>
    </Modal>
  );
}

/** Talabnoma (rasmiy ogohlantirish) yoki da'vo arizasi loyihasi — chop etish uchun */
function DocModal({ row, kind, onClose }: { row: OverdueRow; kind: Doc; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const contract = useQuery({ queryKey: ['contract', row.contractId], queryFn: () => api.get<ContractDetails>(`/api/contracts/${row.contractId}`) });
  const client = useQuery({ queryKey: ['client', row.clientId], queryFn: () => api.get<ClientDetails>(`/api/clients/${row.clientId}`) });
  const { data: settings } = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/api/settings') });
  const qc = useQueryClient();
  const log = useMutation({
    mutationFn: () => api.post(`/api/collection/contracts/${row.contractId}/actions`, {
      actionType: kind === 'demand' ? 'LETTER' : 'LEGAL', result: kind === 'demand' ? t('docDemand') : t('docClaim'),
    }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['overdue'] }),
  });
  const c = contract.data;
  const cl = client.data?.client;
  const company = settings?.companyName ?? settings?.brandName ?? '';
  const deadline = new Date();
  deadline.setDate(deadline.getDate() + 10);
  const address = cl ? [cl.region, cl.district, cl.address].filter(Boolean).join(', ') : '';

  const print = () => {
    log.mutate();
    window.print();
  };

  return (
    <Modal wide title={kind === 'demand' ? t('docDemand') : t('docClaim')} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('close')}</Btn>
        <Btn icon="doc" disabled={!c || !cl} onClick={print}>{t('print')}</Btn>
      </>}>
      <ErrorBox error={contract.error ?? client.error} />
      {c && cl && (
        <div className="print-area legal-doc">
          {kind === 'demand' ? (
            <>
              <div className="doc-to">
                <span>{t('docTo')}: <b>{cl.fullName}</b></span>
                <span>{t('pinfl')}: {cl.pinfl}</span>
                {address && <span>{t('address')}: {address}</span>}
                <span>{t('phone')}: {cl.phone}</span>
              </div>
              <h2>{t('demandHeading')}</h2>
              <p>{t('demandBody1', { company, no: c.contractNo, date: fmtDate(c.createdAt, lang), product: c.productName })}</p>
              <p>{t('demandBody2', { days: row.daysLate, overdue: fmtSom(row.overdueAmount, lang), remaining: fmtSom(c.remaining, lang) })}</p>
              <p>{t('demandBody3', { deadline: fmtDate(deadline, lang) })}</p>
            </>
          ) : (
            <>
              <div className="doc-to">
                <span>{t('claimCourt')}</span>
                <span>{t('claimant')}: <b>{company}</b></span>
                <span>{t('defendant')}: <b>{cl.fullName}</b>, {t('pinfl')}: {cl.pinfl}{address ? `, ${address}` : ''}</span>
                <span>{t('claimAmount')}: <b>{fmtSom(c.remaining, lang)}</b></span>
              </div>
              <h2>{t('claimHeading')}</h2>
              <p>{t('claimBody1', { company, no: c.contractNo, date: fmtDate(c.createdAt, lang), product: c.productName, sale: fmtSom(c.salePrice, lang), term: c.termMonths })}</p>
              <p>{t('claimBody2', { paid: fmtSom(c.paidTotal, lang), remaining: fmtSom(c.remaining, lang), days: row.daysLate })}</p>
              <p>{t('claimBody3')}</p>
              <ol>
                <li>{t('claimAsk1', { remaining: fmtSom(c.remaining, lang) })}</li>
                <li>{t('claimAsk2')}</li>
              </ol>
              <p className="faint" style={{ fontSize: 11.5 }}>{t('claimNote')}</p>
            </>
          )}
          <div className="doc-sign">
            <span>{todayIso()}</span>
            <span>{company} · ____________</span>
          </div>
        </div>
      )}
    </Modal>
  );
}
