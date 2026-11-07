import { Fragment, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { ClientDetails, CreditReport } from '../api/types';
import { Avatar, Badge, Btn, Card, CardHead, ContractStatusBadge, Empty, ErrorBox, Field, Icon, Loading, Modal, RiskPill } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtDate, fmtSom } from '../lib/format';
import ClientForm from './ClientForm';

export default function ClientDetailPage() {
  const { id } = useParams();
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const { can } = useAuth();
  const [editing, setEditing] = useState(false);
  const { data, error, isLoading } = useQuery({
    queryKey: ['client', Number(id)],
    queryFn: () => api.get<ClientDetails>(`/api/clients/${id}`),
  });
  if (isLoading) return <Loading />;
  if (!data) return <div className="page"><ErrorBox error={error} /></div>;
  const c = data.client;

  const rows: [string, string | undefined][] = [
    [t('pinfl'), c.pinfl], [t('phone'), c.phone], [t('phone2'), c.extraPhone],
    [t('passport'), c.passportSeries ? `${c.passportSeries} · ${fmtDate(c.passportExpiry, lang)}` : undefined],
    [t('birth'), c.birthDate ? fmtDate(c.birthDate, lang) : undefined],
    [t('region'), [c.region, c.district].filter(Boolean).join(', ')], [t('address'), c.address],
    [t('job'), c.workplace], [t('income'), c.monthlyIncome != null ? fmtSom(c.monthlyIncome, lang) : undefined],
    [t('family'), c.familyStatus],
  ];

  return (
    <div className="page">
      <Link to="/clients" className="back-link" style={{ textDecoration: 'none' }}><Icon name="chevL" size={16} />{t('clients')}</Link>
      <div className="detail-grid">
        <Card>
          <CardHead title={t('contracts')} sub={`${t('remaining')}: ${fmtSom(data.activeDebt, lang)}`}
            right={can('CONTRACTS_CREATE') && (
              <Btn size="sm" icon="plus" onClick={() => nav(`/new-credit?clientId=${c.id}`)}>{t('newCredit')}</Btn>
            )} />
          <div className="table-wrap">
            <table className="tbl">
              <thead><tr>
                <th>{t('contractNo')}</th><th>{t('product')}</th><th className="num">{t('salePrice')}</th>
                <th className="num">{t('remaining')}</th><th>{t('nextPay')}</th><th>{t('status')}</th><th>{t('risk')}</th>
              </tr></thead>
              <tbody>
                {data.contracts.map((x) => (
                  <tr key={x.id} onClick={() => nav(`/contracts/${x.id}`)}>
                    <td className="cell-main mono"><b>{x.contractNo}</b></td>
                    <td data-label={t('product')}>{x.productName}</td>
                    <td className="num" data-label={t('salePrice')}>{fmtSom(x.salePrice, lang)}</td>
                    <td className="num" data-label={t('remaining')}>{fmtSom(x.remaining, lang)}</td>
                    <td data-label={t('nextPay')}>{fmtDate(x.nextDue, lang)}</td>
                    <td data-label={t('status')}><ContractStatusBadge status={x.status} /></td>
                    <td data-label={t('risk')}><RiskPill cat={x.riskCategory} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data.contracts.length === 0 && <Empty />}
        </Card>
        <Card>
          <div className="card-pad col" style={{ gap: 16 }}>
            <div className="row" style={{ gap: 14 }}>
              <Avatar name={c.fullName} size={52} />
              <div className="col" style={{ gap: 2 }}>
                <b className="display" style={{ fontSize: 19 }}>{c.fullName}</b>
                {c.blacklisted && <span className="badge badge-danger">{t('s_BLACKLIST')}</span>}
              </div>
              {can('CLIENTS_EDIT') && (
                <Btn variant="ghost" size="sm" icon="pen" style={{ marginLeft: 'auto' }} onClick={() => setEditing(true)}>{t('edit')}</Btn>
              )}
            </div>
            <dl className="kv">
              {rows.filter(([, v]) => v).map(([k, v]) => (<Fragment key={k}><dt>{k}</dt><dd>{v}</dd></Fragment>))}
            </dl>
            {c.note && <p className="muted" style={{ margin: 0 }}>{c.note}</p>}
          </div>
        </Card>
      </div>
      <CreditReportsCard clientId={c.id} />
      {editing && <ClientForm initial={c} onClose={() => setEditing(false)} />}
    </div>
  );
}

const RATING_TONE = { A: 'success', B: 'success', C: 'warn', D: 'danger', E: 'danger' } as const;

/** Kredit tarixi (KATM / tashqi byuro): ma'lumotlar qo'lda kiritiladi, oxirgisi nasiya wizard'ida ko'rinadi. */
export function CreditReportsCard({ clientId }: { clientId: number }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const [adding, setAdding] = useState(false);
  const q = useQuery({ queryKey: ['credit-reports', clientId], queryFn: () => api.get<CreditReport[]>(`/api/clients/${clientId}/credit-reports`) });
  return (
    <Card className="block">
      <CardHead title={<><Icon name="shieldCheck" size={16} /> {t('creditHistory')}</>} sub={t('creditHistorySub')}
        right={can('CLIENTS_EDIT') && <Btn size="sm" icon="plus" onClick={() => setAdding(true)}>{t('addReport')}</Btn>} />
      <ErrorBox error={q.error} />
      <div className="table-wrap">
        <table className="tbl">
          <thead><tr>
            <th>{t('date')}</th><th>{t('source')}</th><th>{t('rating')}</th><th className="num">{t('score')}</th>
            <th className="num">{t('activeLoans')}</th><th className="num">{t('totalDebtShort')}</th><th className="num">{t('monthlyObligation')}</th><th>{t('overdue')}</th>
          </tr></thead>
          <tbody>
            {q.data?.map((r) => (
              <tr key={r.id} style={{ cursor: 'default' }}>
                <td className="cell-main"><b>{fmtDate(r.reportDate, lang)}</b></td>
                <td data-label={t('source')}>{r.source}</td>
                <td data-label={t('rating')}>{r.rating ? <Badge tone={RATING_TONE[r.rating]}>{r.rating}</Badge> : '—'}</td>
                <td className="num mono" data-label={t('score')}>{r.score ?? '—'}</td>
                <td className="num mono" data-label={t('activeLoans')}>{r.activeLoans}</td>
                <td className="num mono" data-label={t('totalDebtShort')}>{fmtSom(r.totalDebt, lang)}</td>
                <td className="num mono" data-label={t('monthlyObligation')}>{fmtSom(r.monthlyObligation, lang)}</td>
                <td data-label={t('overdue')}>{r.overdueFlag ? <Badge tone="danger" dot>{r.maxOverdueDays} {t('days')}</Badge> : <Badge tone="success"><Icon name="check" size={13} /></Badge>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {q.data && q.data.length === 0 && <Empty>{t('creditHistoryEmpty')}</Empty>}
      {adding && <CreditReportModal clientId={clientId} onClose={() => setAdding(false)} />}
    </Card>
  );
}

function CreditReportModal({ clientId, onClose }: { clientId: number; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [d, setD] = useState({
    source: 'KATM', reportDate: new Date().toISOString().slice(0, 10), score: '', rating: '', activeLoans: 0,
    totalDebt: 0, monthlyObligation: 0, overdueFlag: false, maxOverdueDays: 0, note: '',
  });
  const save = useMutation({
    mutationFn: () => api.post(`/api/clients/${clientId}/credit-reports`, {
      ...d, score: d.score === '' ? null : Number(d.score), rating: d.rating || null, note: d.note || null,
    }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['credit-reports', clientId] }); onClose(); },
  });
  const num = (k: 'activeLoans' | 'totalDebt' | 'monthlyObligation' | 'maxOverdueDays', label: string) => (
    <Field label={label}><input className="input mono" type="number" min={0} value={d[k]} onChange={(e) => setD({ ...d, [k]: Number(e.target.value) })} /></Field>
  );
  return (
    <Modal wide title={t('addReport')} onClose={onClose}
      footer={<><Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn><Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn></>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <span className="faint" style={{ fontSize: 12 }}>{t('creditManualHint')}</span>
        <div className="form-grid">
          <Field label={t('source')}><input className="input" value={d.source} maxLength={30} onChange={(e) => setD({ ...d, source: e.target.value })} /></Field>
          <Field label={t('date')} req><input className="input" type="date" value={d.reportDate} onChange={(e) => setD({ ...d, reportDate: e.target.value })} /></Field>
          <Field label={t('score')}><input className="input mono" type="number" min={0} max={1000} value={d.score} onChange={(e) => setD({ ...d, score: e.target.value })} /></Field>
          <Field label={t('rating')}>
            <select className="select" value={d.rating} onChange={(e) => setD({ ...d, rating: e.target.value })}>
              <option value="">—</option>{['A', 'B', 'C', 'D', 'E'].map((r) => <option key={r} value={r}>{r}</option>)}
            </select>
          </Field>
          {num('activeLoans', t('activeLoans'))}
          {num('totalDebt', t('totalDebtShort'))}
          {num('monthlyObligation', t('monthlyObligation'))}
          {num('maxOverdueDays', t('maxOverdueDays'))}
        </div>
        <label className="check-row"><input type="checkbox" checked={d.overdueFlag} onChange={(e) => setD({ ...d, overdueFlag: e.target.checked })} />{t('hasOverdue')}</label>
        <Field label={t('note')}><input className="input" maxLength={500} value={d.note} onChange={(e) => setD({ ...d, note: e.target.value })} /></Field>
      </div>
    </Modal>
  );
}
