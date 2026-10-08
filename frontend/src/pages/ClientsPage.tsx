import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { ClientListItem, ClientsSummary, Page } from '../api/types';
import { Avatar, Btn, Card, Empty, ErrorBox, Icon, RiskPill, Stat } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtMln } from '../lib/format';
import ClientForm from './ClientForm';

export default function ClientsPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const { hasRole } = useAuth();
  const canCredit = hasRole('ADMIN', 'CREDIT_OFFICER');
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [page, setPage] = useState(0);
  const [adding, setAdding] = useState(false);

  useEffect(() => {
    const h = setTimeout(() => { setDebounced(q.trim()); setPage(0); }, 300);
    return () => clearTimeout(h);
  }, [q]);

  const { data, error } = useQuery({
    queryKey: ['clients', debounced, page],
    queryFn: () => api.get<Page<ClientListItem>>(`/api/clients${qs({ q: debounced, page, size: 20 })}`),
  });
  const { data: sum } = useQuery({
    queryKey: ['clients-summary'],
    queryFn: () => api.get<ClientsSummary>('/api/clients/summary'),
  });

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="clients" size={18} /></span>{t('clientsBase')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('clientsBaseSub')}</span>
        </div>
        {canCredit && <Btn icon="plus" size="sm" onClick={() => setAdding(true)}>{t('newClient')}</Btn>}
      </div>

      {sum && (
        <div className="kpi4" style={{ marginTop: 0 }}>
          <Stat icon="clients" label={t('clientsTotal')} value={`${sum.total} ${t('pcs')}`}
            sub={<>{t('baseActive')}</>} />
          <Stat icon="wallet" tone="success" label={t('clientsWithDebt')} value={`${sum.withDebt} ${t('pcs')}`}
            sub={<>{t('st_late')}: <b>{sum.lateClients}</b></>} />
          <Stat icon="star" tone="info" label={t('avgScore')} value={sum.avgScore != null ? `${sum.avgScore}` : '—'}
            sub={<>{t('gradeAShare')}: <b>{sum.gradeAPct ?? 0}%</b></>} />
          <Stat icon="trend" tone="gold" label={t('activeDebtTotal')} value={fmtMln(sum.totalDebt, lang)}
            sub={<>{t('regularPayers')}: <b>{sum.withDebt - sum.lateClients}</b></>} />
        </div>
      )}

      <ErrorBox error={error} />
      <Card className="block">
        <div className="card-pad" style={{ paddingTop: 14, paddingBottom: 14 }}>
          <div className="search" style={{ minWidth: 0, width: '100%' }}>
            <Icon name="search" />
            <input placeholder={t('clientsSearch')} value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
        </div>
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('clientNameAddr')}</th><th>{t('pinflPhone')}</th><th>{t('jobIncome')}</th>
              <th>{t('scoreRisk')}</th><th className="num">{t('activeDebtCol')}</th>
              <th className="num" title={t('limitHint')}>{t('internalLimit')}</th>
              {canCredit && <th />}
            </tr></thead>
            <tbody>
              {data?.content.map((c) => (
                <tr key={c.id} onClick={() => nav(`/clients/${c.id}`)}>
                  <td className="cell-main">
                    <div className="cell-client">
                      <Avatar name={c.fullName} size={32} />
                      <div className="col" style={{ gap: 1, minWidth: 0 }}>
                        <b>{c.fullName} {c.blacklisted && <span className="tag-danger mono">{t('s_BLACKLIST')}</span>}</b>
                        <span>{[c.region, c.district].filter(Boolean).join(', ') || '—'}</span>
                      </div>
                    </div>
                  </td>
                  <td data-label={t('pinflPhone')}>
                    <div className="col" style={{ gap: 1 }}>
                      <span className="mono" style={{ fontSize: 11.5 }}>{c.pinfl}</span>
                      <span className="mono faint" style={{ fontSize: 11 }}>{c.phone}</span>
                    </div>
                  </td>
                  <td data-label={t('jobIncome')}>
                    <div className="col" style={{ gap: 1, whiteSpace: 'normal', maxWidth: 220 }}>
                      <span style={{ fontSize: 12 }}>{c.workplace || '—'}</span>
                      <span className="mono faint" style={{ fontSize: 11 }}>{c.monthlyIncome ? fmtMln(c.monthlyIncome, lang) : '—'}</span>
                    </div>
                  </td>
                  <td data-label={t('scoreRisk')}>
                    {c.lastRisk
                      ? <div className="row" style={{ gap: 6 }}><RiskPill cat={c.lastRisk} /><span className="mono faint" style={{ fontSize: 11.5 }}>{c.lastScore}</span></div>
                      : <span className="faint" style={{ fontSize: 12 }}>{t('noHistory')}</span>}
                  </td>
                  <td className="num" data-label={t('activeDebtCol')}>
                    <div className="col" style={{ gap: 1, alignItems: 'flex-end' }}>
                      <b style={{ color: c.hasLate ? 'var(--danger)' : undefined }}>{c.activeDebt > 0 ? fmtMln(c.activeDebt, lang) : '—'}</b>
                      {c.openContracts > 0 && <span className="faint" style={{ fontSize: 11 }}>{c.openContracts} {t('contractsShort')}</span>}
                    </div>
                  </td>
                  <td className="num" data-label={t('internalLimit')}>
                    <b style={{ color: c.creditLimit > 0 ? 'var(--success)' : 'var(--text-faint)' }}>{fmtMln(c.creditLimit, lang)}</b>
                  </td>
                  {canCredit && (
                    <td data-label="" onClick={(e) => e.stopPropagation()}>
                      <Btn size="sm" variant="ghost" icon="newcredit" disabled={c.blacklisted}
                        onClick={() => nav(`/new-credit?clientId=${c.id}`)}>{t('newCredit')}</Btn>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.content.length === 0 && <Empty>{t('noResults')}</Empty>}
        {data && data.totalPages > 1 && (
          <div className="pager">
            <Btn variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}><Icon name="chevL" size={16} /></Btn>
            <span className="faint mono">{page + 1} / {data.totalPages}</span>
            <Btn variant="ghost" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}><Icon name="chevR" size={16} /></Btn>
          </div>
        )}
      </Card>
      {adding && <ClientForm onClose={() => setAdding(false)} onSaved={(c) => nav(`/clients/${c.id}`)} />}
    </div>
  );
}
