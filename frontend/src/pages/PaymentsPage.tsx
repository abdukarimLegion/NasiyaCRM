import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { Page, PaymentDto } from '../api/types';
import { Card, CardHead, Empty, ErrorBox, Field } from '../components/ui';
import { fmtDate, fmtSom, todayIso } from '../lib/format';

export default function PaymentsPage() {
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

  return (
    <div className="page">
      <div className="row wrap" style={{ gap: 12, marginBottom: 18, alignItems: 'flex-end' }}>
        <Field label={t('from')}><input className="input" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
        <Field label={t('to')}><input className="input" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
      </div>
      <ErrorBox error={error} />
      <Card>
        <CardHead title={t('payments')} sub={`${t('total')}: ${fmtSom(total, lang)} · ${data?.totalElements ?? 0}`} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('date')}</th><th>{t('contractNo')}</th><th>{t('client')}</th>
              <th>{t('method')}</th><th className="num">{t('amount')}</th><th>{t('note')}</th>
            </tr></thead>
            <tbody>
              {data?.content.map((p) => (
                <tr key={p.id} onClick={() => nav(`/contracts/${p.contractId}`)}>
                  <td data-label={t('date')}>{fmtDate(p.paidAt, lang)}</td>
                  <td className="mono" data-label={t('contractNo')}>{p.contractNo}</td>
                  <td className="cell-main"><b>{p.clientName}</b></td>
                  <td data-label={t('method')}>{t(`m_${p.method}`)}</td>
                  <td className="num" data-label={t('amount')}>{fmtSom(p.amount, lang)}</td>
                  <td className="muted" data-label={t('note')}>{p.note}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.content.length === 0 && <Empty />}
      </Card>
    </div>
  );
}
