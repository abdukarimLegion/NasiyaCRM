import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { ActionType, CollectionActionDto, CollectionStage, OverdueRow } from '../api/types';
import { Badge, Btn, Card, CardHead, Empty, ErrorBox, Field, Modal, type Tone } from '../components/ui';
import { fmtDate, fmtSom } from '../lib/format';

const STAGES: CollectionStage[] = ['REMINDER', 'SOFT', 'HARD', 'LEGAL'];
const STAGE_KEY: Record<CollectionStage, string> = { REMINDER: 'reminder', SOFT: 'soft', HARD: 'hard', LEGAL: 'legal' };
const STAGE_TONE: Record<CollectionStage, Tone> = { REMINDER: 'info', SOFT: 'warn', HARD: 'danger', LEGAL: 'danger' };
const ACTIONS: ActionType[] = ['CALL', 'SMS', 'VISIT', 'LETTER', 'LEGAL', 'NOTE'];

export default function CollectionPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const [selected, setSelected] = useState<OverdueRow | null>(null);
  const { data, error } = useQuery({ queryKey: ['overdue'], queryFn: () => api.get<OverdueRow[]>('/api/collection/overdue') });

  return (
    <div className="page">
      <div className="funnel" style={{ marginBottom: 22 }}>
        {STAGES.map((s, i) => {
          const rows = data?.filter((r) => r.stage === s) ?? [];
          return (
            <div key={s} className={`funnel-card fc-${s.toLowerCase()}`}>
              <span className="funnel-step">0{i + 1}</span>
              <span className="faint" style={{ fontSize: 13 }}>{t(STAGE_KEY[s])}</span>
              <b className="display" style={{ fontSize: 24 }}>{rows.length}</b>
              <span className="muted">{fmtSom(rows.reduce((a, r) => a + r.overdueAmount, 0), lang)}</span>
            </div>
          );
        })}
      </div>
      <ErrorBox error={error} />
      <Card>
        <CardHead title={t('collection')} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('client')}</th><th>{t('contractNo')}</th><th>{t('phone')}</th>
              <th className="num">{t('overdue')}</th><th>{t('daysLate')}</th><th>{t('status')}</th><th>{t('lastAction')}</th>
            </tr></thead>
            <tbody>
              {data?.map((r) => (
                <tr key={r.contractId} onClick={() => setSelected(r)}>
                  <td><Link to={`/clients/${r.clientId}`} onClick={(e) => e.stopPropagation()}><b>{r.clientName}</b></Link></td>
                  <td className="mono">{r.contractNo}</td>
                  <td className="mono"><a href={`tel:${r.phone}`} onClick={(e) => e.stopPropagation()}>{r.phone}</a></td>
                  <td className="num">{fmtSom(r.overdueAmount, lang)}</td>
                  <td>{r.daysLate} {t('daysLateShort')}</td>
                  <td><Badge tone={STAGE_TONE[r.stage]} dot>{t(STAGE_KEY[r.stage])}</Badge></td>
                  <td>{fmtDate(r.lastActionDate, lang)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.length === 0 && <Empty>{t('noOverdue')}</Empty>}
      </Card>
      {selected && <ActionsModal row={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}

function ActionsModal({ row, onClose }: { row: OverdueRow; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [actionType, setActionType] = useState<ActionType>('CALL');
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
