import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { ContractDetails, PaymentDto, PaymentMethod } from '../api/types';
import {
  Bar, Btn, Card, CardHead, ContractStatusBadge, ErrorBox, Field, Icon, Loading, Modal, RiskPill, ScheduleStatusBadge,
} from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtDate, fmtSom } from '../lib/format';

const METHODS: PaymentMethod[] = ['CASH', 'CARD', 'PAYME', 'CLICK', 'UZUM', 'BANK_TRANSFER'];

export default function ContractDetailPage() {
  const { id } = useParams();
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const qc = useQueryClient();
  const { hasRole } = useAuth();
  const [paying, setPaying] = useState(false);
  const { data: c, error, isLoading } = useQuery({
    queryKey: ['contract', Number(id)],
    queryFn: () => api.get<ContractDetails>(`/api/contracts/${id}`),
  });
  const cancel = useMutation({
    mutationFn: () => api.post<ContractDetails>(`/api/contracts/${id}/cancel`),
    onSuccess: (d) => qc.setQueryData(['contract', Number(id)], d),
  });

  if (isLoading) return <Loading />;
  if (!c) return <div className="page"><ErrorBox error={error} /></div>;
  const open = c.status === 'ACTIVE' || c.status === 'LATE';
  const pct = c.installmentTotal > 0 ? (c.paidTotal / c.installmentTotal) * 100 : 0;

  return (
    <div className="page">
      <Link to="/contracts" className="back-link" style={{ textDecoration: 'none' }}><Icon name="chevL" size={16} />{t('contracts')}</Link>
      <div className="detail-grid">
        <Card>
          <CardHead title={t('schedule')} sub={`${fmtSom(c.monthlyPayment, lang)} × ${c.termMonths}`}
            right={open && hasRole('ADMIN', 'CASHIER', 'CREDIT_OFFICER') && (
              <Btn size="sm" icon="card" onClick={() => setPaying(true)}>{t('payNow')}</Btn>
            )} />
          <div className="table-wrap">
            <table className="tbl">
              <thead><tr>
                <th>#</th><th>{t('date')}</th><th className="num">{t('amount')}</th>
                <th className="num">{t('principalPart')}</th><th className="num">{t('markupPart')}</th>
                <th className="num">{t('paid')}</th><th>{t('status')}</th>
              </tr></thead>
              <tbody>
                {c.schedule.map((s) => (
                  <tr key={s.id} style={{ cursor: 'default' }}>
                    <td className="faint hide-sm">{s.seq}</td>
                    <td className="cell-main"><b>{fmtDate(s.dueDate, lang)}</b></td>
                    <td className="num" data-label={t('amount')}>{fmtSom(s.amount, lang)}</td>
                    <td className="num" data-label={t('principalPart')}>{fmtSom(s.principalPart, lang)}</td>
                    <td className="num" data-label={t('markupPart')}>{fmtSom(s.markupPart, lang)}</td>
                    <td className="num" data-label={t('paid')}>{fmtSom(s.paidAmount, lang)}</td>
                    <td data-label={t('status')}><ScheduleStatusBadge status={s.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
        <Card>
          <div className="card-pad col" style={{ gap: 16 }}>
            <div className="row between">
              <b className="display mono" style={{ fontSize: 18 }}>{c.contractNo}</b>
              <ContractStatusBadge status={c.status} />
            </div>
            <Link to={`/clients/${c.clientId}`} style={{ fontWeight: 600 }}>{c.clientName}</Link>
            <div className="col" style={{ gap: 6 }}>
              <div className="row between"><span className="faint">{t('paid')}</span><b>{Math.round(pct)}%</b></div>
              <Bar pct={pct} />
            </div>
            <dl className="kv">
              <dt>{t('product')}</dt><dd>{c.productName}</dd>
              <dt>{t('productPrice')}</dt><dd>{fmtSom(c.costPrice, lang)}</dd>
              <dt>{t('downPayment')}</dt><dd>{fmtSom(c.downPayment, lang)}</dd>
              <dt>{t('profit')} ({c.markupPct}%)</dt><dd>{fmtSom(c.markupAmount, lang)}</dd>
              <dt>{t('salePrice')}</dt><dd>{fmtSom(c.salePrice, lang)}</dd>
              <dt>{t('paid')}</dt><dd>{fmtSom(c.paidTotal, lang)}</dd>
              <dt>{t('remaining')}</dt><dd>{fmtSom(c.remaining, lang)}</dd>
              <dt>{t('risk')}</dt><dd><RiskPill cat={c.riskCategory} /> {c.scoreTotal}</dd>
              {c.guarantorName && <><dt>{t('guarantorName')}</dt><dd>{c.guarantorName}</dd></>}
              <dt>{t('date')}</dt><dd>{fmtDate(c.createdAt, lang)}</dd>
            </dl>
            <ErrorBox error={cancel.error} />
            {hasRole('ADMIN') && open && c.paidTotal === 0 && (
              <Btn variant="ghost" size="sm" icon="x" onClick={() => cancel.mutate()}>{t('cancelContract')}</Btn>
            )}
          </div>
        </Card>
      </div>
      {paying && <PaymentModal contract={c} onClose={() => setPaying(false)} />}
    </div>
  );
}

function PaymentModal({ contract, onClose }: { contract: ContractDetails; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const nextDue = contract.schedule.find((s) => s.status !== 'PAID');
  const [amount, setAmount] = useState<number>(nextDue ? nextDue.amount - nextDue.paidAmount : 0);
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [note, setNote] = useState('');
  const pay = useMutation({
    mutationFn: () => api.post<PaymentDto>('/api/payments', { contractId: contract.id, amount, method, note: note || undefined }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['contract', contract.id] });
      void qc.invalidateQueries({ queryKey: ['payments'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      onClose();
    },
  });
  return (
    <Modal title={`${t('payNow')} · ${contract.contractNo}`} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={pay.isPending || amount <= 0} onClick={() => pay.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={pay.error} />
        <Field label={t('amount')} req hint={`${t('remaining')}: ${fmtSom(contract.remaining, i18n.language)}`}>
          <input className="input" type="number" min={1} max={contract.remaining} value={amount}
            onChange={(e) => setAmount(Number(e.target.value))} />
        </Field>
        <Field label={t('method')}>
          <select className="select" value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            {METHODS.map((m) => <option key={m} value={m}>{t(`m_${m}`)}</option>)}
          </select>
        </Field>
        <Field label={t('note')}>
          <input className="input" value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}
