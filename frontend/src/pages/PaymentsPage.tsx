import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type {
  ContractDetails, ContractListItem, Page, PaymentDto, PaymentMethod, Settings,
} from '../api/types';
import {
  Btn, Card, CardHead, Empty, ErrorBox, Field, Icon, Modal, ScheduleStatusBadge, Seg,
} from '../components/ui';
import { useAuth } from '../lib/auth';
import { daysSince, fmtDate, fmtDateTime, fmtMln, fmtSom, todayIso } from '../lib/format';

const METHODS: PaymentMethod[] = ['CASH', 'CARD', 'PAYME', 'CLICK', 'UZUM', 'BANK_TRANSFER'];

export default function PaymentsPage() {
  const { t } = useTranslation();
  const { hasRole } = useAuth();
  const canAccept = hasRole('ADMIN', 'CASHIER', 'CREDIT_OFFICER');
  const [tab, setTab] = useState<'cashier' | 'history'>(canAccept ? 'cashier' : 'history');

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="wallet" size={18} /></span>{t('cashierTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('cashierSub')}</span>
        </div>
        {canAccept && (
          <Seg value={tab} onChange={setTab} options={[
            { value: 'cashier', label: t('cashierTab') },
            { value: 'history', label: t('historyTab') },
          ]} />
        )}
      </div>
      {tab === 'cashier' ? <Cashier /> : <History />}
    </div>
  );
}

/* ------------------------------------------------------------------ Kassa */

function Cashier() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const [query, setQuery] = useState('');
  const [debounced, setDebounced] = useState('');
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [receipt, setReceipt] = useState<{ payment: PaymentDto; contract: ContractDetails } | null>(null);

  useEffect(() => {
    const h = setTimeout(() => setDebounced(query.trim()), 300);
    return () => clearTimeout(h);
  }, [query]);

  const list = useQuery({
    queryKey: ['cashier-contracts', debounced],
    queryFn: () => api.get<Page<ContractListItem>>(`/api/contracts${qs({ open: true, q: debounced, size: 50 })}`),
  });
  const rows = list.data?.content ?? [];
  const activeId = selectedId ?? rows[0]?.id ?? null;

  return (
    <div className="cashier-grid">
      <Card className="cashier-list">
        <CardHead title={t('findContract')} sub={`${list.data?.totalElements ?? 0} ${t('openContracts')}`} />
        <div className="card-pad" style={{ paddingTop: 14, paddingBottom: 10 }}>
          <div className="search" style={{ minWidth: 0, width: '100%' }}>
            <Icon name="search" />
            <input placeholder={t('cashierSearch')} value={query} onChange={(e) => setQuery(e.target.value)} />
          </div>
        </div>
        <div className="pick-list scroll">
          {rows.map((c) => {
            const late = c.status === 'LATE' && c.nextDue ? daysSince(c.nextDue) : 0;
            return (
              <button key={c.id} className="pick-item" data-on={c.id === activeId} onClick={() => setSelectedId(c.id)}>
                <div className="row between" style={{ gap: 8 }}>
                  <b className="pick-name">{c.clientName}</b>
                  <span className={`mono pick-state ${late > 0 ? 'is-late' : ''}`}>
                    {late > 0 ? t('daysLateN', { n: late }) : t('st_active')}
                  </span>
                </div>
                <span className="mono faint" style={{ fontSize: 11 }}>{c.contractNo} · {c.clientPhone}</span>
                <div className="row between" style={{ fontSize: 12 }}>
                  <span className="faint">{t('remaining')}:</span>
                  <b className="mono">{fmtMln(c.remaining, lang)}</b>
                </div>
              </button>
            );
          })}
          {list.data && rows.length === 0 && <Empty>{t('noResults')}</Empty>}
          <ErrorBox error={list.error} />
        </div>
      </Card>

      <div className="col" style={{ gap: 20, minWidth: 0 }}>
        {activeId != null
          ? <ContractCashier id={activeId} onPaid={(payment, contract) => setReceipt({ payment, contract })} />
          : <Card><Empty>{t('selectContract')}</Empty></Card>}
      </div>

      {receipt && <ReceiptModal {...receipt} onClose={() => setReceipt(null)} />}
    </div>
  );
}

function ContractCashier({ id, onPaid }: { id: number; onPaid: (p: PaymentDto, c: ContractDetails) => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const qc = useQueryClient();
  const { data: c, error } = useQuery({
    queryKey: ['contract', id],
    queryFn: () => api.get<ContractDetails>(`/api/contracts/${id}`),
  });
  const [amount, setAmount] = useState(0);
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [note, setNote] = useState('');

  const open = c?.schedule.filter((s) => s.status !== 'PAID') ?? [];
  const next = open[0];
  const overdue = open.filter((s) => s.status === 'LATE').reduce((a, s) => a + s.amount - s.paidAmount, 0);
  const nextDueAmount = next ? next.amount - next.paidAmount : 0;

  // Shartnoma almashganda summa navbatdagi to'lovga (kechikkan bo'lsa — kechikkan qarzga) qo'yiladi
  useEffect(() => {
    if (c) setAmount(overdue > 0 ? overdue : nextDueAmount);
    setNote('');
    // faqat boshqa shartnoma tanlanganda (to'lovdan keyin emas)
  }, [c?.id]);

  const pay = useMutation({
    mutationFn: () => api.post<PaymentDto>('/api/payments', { contractId: id, amount, method, note: note || undefined }),
    onSuccess: async (p) => {
      const fresh = await api.get<ContractDetails>(`/api/contracts/${id}`);
      qc.setQueryData(['contract', id], fresh);
      void qc.invalidateQueries({ queryKey: ['cashier-contracts'] });
      void qc.invalidateQueries({ queryKey: ['payments'] });
      void qc.invalidateQueries({ queryKey: ['dashboard'] });
      onPaid(p, fresh);
    },
  });

  if (!c) return <Card><div className="card-pad"><ErrorBox error={error} /></div></Card>;
  const late = next && next.status === 'LATE' ? daysSince(next.dueDate) : 0;
  const tooMuch = amount > c.remaining;

  return (
    <>
      <Card>
        <div className="card-pad col" style={{ gap: 16 }}>
          <div className="row between" style={{ alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
            <div className="col" style={{ gap: 4, minWidth: 0 }}>
              <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                <b style={{ fontSize: 14.5 }}>{c.contractNo} — {c.clientName}</b>
                {late > 0 && <span className="tag-danger mono">{t('daysLateN', { n: late })}</span>}
              </div>
              <span className="mono faint" style={{ fontSize: 11.5 }}>{t('phone')}: {c.clientPhone} · {t('product')}: {c.productName}</span>
            </div>
            <div className="col" style={{ alignItems: 'flex-end', gap: 2 }}>
              <span className="faint" style={{ fontSize: 11.5 }}>{t('totalRemaining')}</span>
              <b className="mono" style={{ fontSize: 19, color: 'var(--gold)' }}>{fmtSom(c.remaining, lang)}</b>
            </div>
          </div>
          <div className="well-grid">
            <div className="well"><span className="well-label">{t('installmentTotalShort')}</span><b className="well-val sm">{fmtMln(c.installmentTotal, lang)}</b></div>
            <div className="well"><span className="well-label">{t('paid')}</span><b className="well-val sm" style={{ color: 'var(--success)' }}>{fmtMln(c.paidTotal, lang)}</b></div>
            <div className="well"><span className="well-label">{t('monthlyPayment')}</span><b className="well-val sm">{fmtMln(c.monthlyPayment, lang)}</b></div>
            <div className="well"><span className="well-label">{t('nextPay')}</span><b className="well-val sm" style={{ color: late > 0 ? 'var(--danger)' : 'var(--success)' }}>{next ? fmtDate(next.dueDate, lang) : '—'}</b></div>
          </div>

          <div className="pay-box">
            <b className="row" style={{ gap: 7, fontSize: 13 }}><Icon name="card" size={16} />{t('acceptPayment')}</b>
            <div className="pay-form">
              <Field label={t('amountSom')} err={tooMuch ? t('amountTooMuch') : undefined}>
                <input className="input mono" type="number" min={1} max={c.remaining} value={amount || ''}
                  data-err={tooMuch} style={{ fontWeight: 700 }} onChange={(e) => setAmount(Number(e.target.value))} />
                <div className="row" style={{ gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
                  {nextDueAmount > 0 && <button className="chip-sm" onClick={() => setAmount(nextDueAmount)}>{t('nextShort')}: {fmtMln(nextDueAmount, lang)}</button>}
                  {overdue > 0 && <button className="chip-sm is-danger" onClick={() => setAmount(overdue)}>{t('overdue')}: {fmtMln(overdue, lang)}</button>}
                  <button className="chip-sm" onClick={() => setAmount(c.remaining)}>{t('payOff')}: {fmtMln(c.remaining, lang)}</button>
                </div>
              </Field>
              <Field label={t('method')}>
                <div className="method-grid">
                  {METHODS.map((m) => (
                    <button key={m} className="method-btn" data-on={method === m} onClick={() => setMethod(m)}>{t(`m_${m}`)}</button>
                  ))}
                </div>
              </Field>
              <Field label={t('noteReceipt')}>
                <input className="input" value={note} placeholder={t('notePlaceholder')} onChange={(e) => setNote(e.target.value)} />
                <Btn icon="check" style={{ marginTop: 8, width: '100%' }}
                  disabled={pay.isPending || amount <= 0 || tooMuch} onClick={() => pay.mutate()}>{t('settlePayment')}</Btn>
              </Field>
            </div>
            <ErrorBox error={pay.error} />
          </div>
        </div>
      </Card>

      <Card>
        <CardHead title={t('scheduleState')} sub={`${fmtSom(c.monthlyPayment, lang)} × ${c.termMonths}`} />
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
    </>
  );
}

/* ------------------------------------------------------------------ Kvitansiya */

function ReceiptModal({ payment, contract, onClose }: { payment: PaymentDto; contract: ContractDetails; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { data: settings } = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/api/settings') });
  const next = contract.schedule.find((s) => s.status !== 'PAID');
  const receiptNo = `KV-${String(payment.id).padStart(6, '0')}`;

  return (
    <Modal title={t('receiptTitle')} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('close')}</Btn>
        <Btn icon="doc" onClick={() => window.print()}>{t('print')}</Btn>
      </>}>
      <div className="receipt print-area">
        <div className="col center" style={{ gap: 2, textAlign: 'center' }}>
          <b style={{ fontSize: 15 }}>{settings?.companyName ?? settings?.brandName}</b>
          <span className="faint" style={{ fontSize: 11.5 }}>{t('receiptTitle')}</span>
          <span className="mono" style={{ fontSize: 12, marginTop: 4 }}>№ {receiptNo}</span>
        </div>
        <div className="receipt-ok"><Icon name="check" size={18} /> {t('paymentAccepted')}</div>
        <dl className="kv receipt-kv">
          <dt>{t('dateTime')}</dt><dd>{fmtDateTime(payment.paidAt, lang)}</dd>
          <dt>{t('client')}</dt><dd>{contract.clientName}</dd>
          <dt>{t('contractNo')}</dt><dd className="mono">{contract.contractNo}</dd>
          <dt>{t('product')}</dt><dd>{contract.productName}</dd>
          <dt>{t('method')}</dt><dd>{t(`m_${payment.method}`)}</dd>
          {payment.cashierName && <><dt>{t('cashier')}</dt><dd>{payment.cashierName}</dd></>}
          {payment.note && <><dt>{t('note')}</dt><dd>{payment.note}</dd></>}
        </dl>
        <div className="receipt-sum">
          <span>{t('acceptedAmount')}</span>
          <b className="mono">{fmtSom(payment.amount, lang)}</b>
        </div>
        <dl className="kv receipt-kv">
          <dt>{t('remainingAfter')}</dt><dd className="mono">{fmtSom(contract.remaining, lang)}</dd>
          <dt>{t('nextPay')}</dt><dd>{next ? `${fmtDate(next.dueDate, lang)} · ${fmtSom(next.amount - next.paidAmount, lang)}` : t('st_closed')}</dd>
        </dl>
        <div className="receipt-sign">
          <span>{t('cashierSign')}: ____________</span>
          <span>{t('clientSign')}: ____________</span>
        </div>
      </div>
    </Modal>
  );
}

/* ------------------------------------------------------------------ Tarix */

function History() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const [from, setFrom] = useState(() => `${todayIso().slice(0, 8)}01`);
  const [to, setTo] = useState(todayIso());
  const { data, error } = useQuery({
    queryKey: ['payments', from, to],
    queryFn: () => api.get<Page<PaymentDto>>(`/api/payments${qs({ from, to, size: 200 })}`),
  });
  const total = data?.content.reduce((s, p) => s + p.amount, 0) ?? 0;
  const byMethod = METHODS.map((m) => ({
    m, sum: data?.content.filter((p) => p.method === m).reduce((s, p) => s + p.amount, 0) ?? 0,
  })).filter((x) => x.sum > 0);

  return (
    <>
      <div className="row wrap" style={{ gap: 12, marginBottom: 18, alignItems: 'flex-end' }}>
        <Field label={t('from')}><input className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
        <Field label={t('to')}><input className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
        <div className="row" style={{ gap: 8, flexWrap: 'wrap', marginLeft: 'auto' }}>
          {byMethod.map((x) => (
            <span key={x.m} className="badge badge-neutral">{t(`m_${x.m}`)}: <b className="mono">{fmtMln(x.sum, lang)}</b></span>
          ))}
        </div>
      </div>
      <ErrorBox error={error} />
      <Card>
        <CardHead title={t('payments')} sub={`${t('total')}: ${fmtSom(total, lang)} · ${data?.totalElements ?? 0} ${t('pcs')}`} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('dateTime')}</th><th>{t('contractNo')}</th><th>{t('client')}</th>
              <th>{t('method')}</th><th className="num">{t('amount')}</th><th>{t('cashier')}</th><th>{t('note')}</th>
            </tr></thead>
            <tbody>
              {data?.content.map((p) => (
                <tr key={p.id} onClick={() => nav(`/contracts/${p.contractId}`)}>
                  <td data-label={t('dateTime')} className="mono">{fmtDateTime(p.paidAt, lang)}</td>
                  <td className="mono" data-label={t('contractNo')}>{p.contractNo}</td>
                  <td className="cell-main"><b>{p.clientName}</b></td>
                  <td data-label={t('method')}>{t(`m_${p.method}`)}</td>
                  <td className="num" data-label={t('amount')}>{fmtSom(p.amount, lang)}</td>
                  <td data-label={t('cashier')}>{p.cashierName ?? '—'}</td>
                  <td className="muted" data-label={t('note')}>{p.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.content.length === 0 && <Empty />}
      </Card>
    </>
  );
}
