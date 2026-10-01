import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { Client, Page } from '../api/types';
import { Avatar, Btn, Card, Empty, ErrorBox, Icon } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtSom } from '../lib/format';
import ClientForm from './ClientForm';

export default function ClientsPage() {
  const { t, i18n } = useTranslation();
  const nav = useNavigate();
  const { hasRole } = useAuth();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);
  const { data, error } = useQuery({
    queryKey: ['clients', q, page],
    queryFn: () => api.get<Page<Client>>(`/api/clients${qs({ q, page, size: 20 })}`),
  });

  return (
    <div className="page">
      <div className="row between wrap" style={{ gap: 12, marginBottom: 18 }}>
        <div className="search" style={{ maxWidth: 360, flex: 1 }}>
          <Icon name="search" />
          <input placeholder={t('search')} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
        </div>
        {hasRole('ADMIN', 'CREDIT_OFFICER') && <Btn icon="plus" size="sm" onClick={() => setAdding(true)}>{t('addClient')}</Btn>}
      </div>
      <ErrorBox error={error} />
      <Card>
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('client')}</th><th>{t('pinfl')}</th><th>{t('phone')}</th><th>{t('region')}</th>
              <th className="num">{t('income')}</th>
            </tr></thead>
            <tbody>
              {data?.content.map((c) => (
                <tr key={c.id} onClick={() => nav(`/clients/${c.id}`)}>
                  <td><div className="cell-client"><Avatar name={c.fullName} size={30} /><b>{c.fullName}</b>
                    {c.blacklisted && <span className="badge badge-danger">!</span>}</div></td>
                  <td className="mono">{c.pinfl}</td>
                  <td className="mono">{c.phone}</td>
                  <td className="muted">{[c.region, c.district].filter(Boolean).join(', ')}</td>
                  <td className="num">{fmtSom(c.monthlyIncome, i18n.language)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.content.length === 0 && <Empty>{t('noResults')}</Empty>}
        {data && data.totalPages > 1 && (
          <div className="pager">
            <Btn variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}><Icon name="chevL" size={16} /></Btn>
            <span className="faint">{page + 1} / {data.totalPages}</span>
            <Btn variant="ghost" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}><Icon name="chevR" size={16} /></Btn>
          </div>
        )}
      </Card>
      {adding && <ClientForm onClose={() => setAdding(false)} onSaved={(c) => nav(`/clients/${c.id}`)} />}
    </div>
  );
}
