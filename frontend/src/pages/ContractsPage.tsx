import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { ContractListItem, ContractStatus, Page } from '../api/types';
import { Avatar, Btn, Card, ContractStatusBadge, Empty, ErrorBox, Icon, RiskPill } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtDate, fmtSom } from '../lib/format';

export default function ContractsPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const { hasRole } = useAuth();
  const [status, setStatus] = useState<ContractStatus | ''>('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const { data, error } = useQuery({
    queryKey: ['contracts', status, q, page],
    queryFn: () => api.get<Page<ContractListItem>>(`/api/contracts${qs({ status, q, page })}`),
  });
  const filters: [ContractStatus | '', string][] = [
    ['', t('all')], ['ACTIVE', t('st_active')], ['LATE', t('st_late')], ['CLOSED', t('st_closed')],
  ];

  return (
    <div className="page">
      <div className="row between wrap" style={{ gap: 12, marginBottom: 18 }}>
        <div className="row gap-sm wrap">
          {filters.map(([v, l]) => (
            <span key={v} className="chip" data-on={status === v} onClick={() => { setStatus(v); setPage(0); }}>{l}</span>
          ))}
          <div className="search" style={{ maxWidth: 260 }}>
            <Icon name="search" /><input placeholder={t('search')} value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
          </div>
        </div>
        {hasRole('ADMIN', 'CREDIT_OFFICER') && <Btn icon="plus" size="sm" onClick={() => nav('/new-credit')}>{t('newCredit')}</Btn>}
      </div>
      <ErrorBox error={error} />
      <Card>
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('contractNo')}</th><th>{t('client')}</th><th>{t('product')}</th>
              <th className="num">{t('salePrice')}</th><th className="num">{t('monthly')}</th><th>{t('term')}</th>
              <th>{t('date')}</th><th>{t('status')}</th><th>{t('risk')}</th>
            </tr></thead>
            <tbody>
              {data?.content.map((c) => (
                <tr key={c.id} onClick={() => nav(`/contracts/${c.id}`)}>
                  <td className="mono" data-label={t('contractNo')}>{c.contractNo}</td>
                  <td className="cell-main"><div className="cell-client"><Avatar name={c.clientName} size={30} /><b>{c.clientName}</b></div></td>
                  <td className="muted" data-label={t('product')}>{c.productName}</td>
                  <td className="num" data-label={t('salePrice')}>{fmtSom(c.salePrice, lang)}</td>
                  <td className="num" data-label={t('monthly')}>{fmtSom(c.monthlyPayment, lang)}</td>
                  <td data-label={t('term')}>{c.termMonths} {t('months')}</td>
                  <td data-label={t('date')}>{fmtDate(c.createdAt, lang)}</td>
                  <td data-label={t('status')}><ContractStatusBadge status={c.status} /></td>
                  <td data-label={t('risk')}><RiskPill cat={c.riskCategory} /></td>
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
    </div>
  );
}
