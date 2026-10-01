import { Fragment, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { ClientDetails } from '../api/types';
import { Avatar, Btn, Card, CardHead, ContractStatusBadge, Empty, ErrorBox, Icon, Loading, RiskPill } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtDate, fmtSom } from '../lib/format';
import ClientForm from './ClientForm';

export default function ClientDetailPage() {
  const { id } = useParams();
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const { hasRole } = useAuth();
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
            right={hasRole('ADMIN', 'CREDIT_OFFICER') && (
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
                    <td className="mono">{x.contractNo}</td>
                    <td>{x.productName}</td>
                    <td className="num">{fmtSom(x.salePrice, lang)}</td>
                    <td className="num">{fmtSom(x.remaining, lang)}</td>
                    <td>{fmtDate(x.nextDue, lang)}</td>
                    <td><ContractStatusBadge status={x.status} /></td>
                    <td><RiskPill cat={x.riskCategory} /></td>
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
              {hasRole('ADMIN', 'CREDIT_OFFICER') && (
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
      {editing && <ClientForm initial={c} onClose={() => setEditing(false)} />}
    </div>
  );
}
