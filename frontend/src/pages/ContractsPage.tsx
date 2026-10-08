import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { ContractListItem, ContractStatus, Page, RiskCategory } from '../api/types';
import { Btn, Card, ContractStatusBadge, Empty, ErrorBox, Icon, RiskPill } from '../components/ui';
import { useAuth } from '../lib/auth';
import { downloadCsv } from '../lib/csv';
import { daysSince, fmtDate, fmtMln, fmtSom } from '../lib/format';

const STATUSES: (ContractStatus | '')[] = ['', 'ACTIVE', 'LATE', 'CLOSED'];
const RISKS: (RiskCategory | '')[] = ['', 'A', 'B', 'C', 'D'];

export default function ContractsPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const { hasRole } = useAuth();
  const [status, setStatus] = useState<ContractStatus | ''>('');
  const [risk, setRisk] = useState<RiskCategory | ''>('');
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    const h = setTimeout(() => { setDebounced(q.trim()); setPage(0); }, 300);
    return () => clearTimeout(h);
  }, [q]);

  const filter = { status, risk, q: debounced };
  const { data, error } = useQuery({
    queryKey: ['contracts', filter, page],
    queryFn: () => api.get<Page<ContractListItem>>(`/api/contracts${qs({ ...filter, page })}`),
  });

  const statusLabel: Record<string, string> = {
    '': t('all'), ACTIVE: t('st_active'), LATE: t('st_late'), CLOSED: t('st_closed'),
  };

  /** Joriy filtr bo'yicha barcha sahifalarni yig'ib CSV qiladi */
  const exportCsv = async () => {
    setExporting(true);
    try {
      const all: ContractListItem[] = [];
      for (let p = 0; ; p++) {
        const res = await api.get<Page<ContractListItem>>(`/api/contracts${qs({ ...filter, page: p, size: 100 })}`);
        all.push(...res.content);
        if (p + 1 >= res.totalPages) break;
      }
      downloadCsv('shartnomalar', [
        t('contractNo'), t('date'), t('client'), t('phone'), t('product'), t('installmentTotalShort'),
        t('downPayment'), t('paid'), t('remaining'), t('monthlyPayment'), t('term'), t('status'), t('risk'), t('score'),
      ], all.map((c) => [
        c.contractNo, c.createdAt.slice(0, 10), c.clientName, c.clientPhone, c.productName, c.installmentTotal,
        c.downPayment, c.paidTotal, c.remaining, c.monthlyPayment, c.termMonths, statusLabel[c.status] ?? c.status,
        c.riskCategory ?? '', c.scoreTotal ?? '',
      ]));
    } finally {
      setExporting(false);
    }
  };

  return (
    <div className="page">
      <Card>
        <div className="card-pad col" style={{ gap: 16 }}>
          <div className="page-head" style={{ marginBottom: 0 }}>
            <div className="col" style={{ gap: 2 }}>
              <h1 className="page-h1"><span className="h1-ic"><Icon name="contracts" size={18} /></span>{t('contractsRegistry')}</h1>
              <span className="faint" style={{ fontSize: 12.5 }}>{t('contractsRegistrySub')}</span>
            </div>
            <div className="row" style={{ gap: 8 }}>
              <Btn variant="ghost" size="sm" icon="download" disabled={exporting || !data?.totalElements} onClick={exportCsv}>
                CSV ({data?.totalElements ?? 0})
              </Btn>
              {hasRole('ADMIN', 'CREDIT_OFFICER') && <Btn icon="plus" size="sm" onClick={() => nav('/new-credit')}>{t('newCredit')}</Btn>}
            </div>
          </div>
          <div className="filter-bar">
            <div className="search" style={{ flex: 1, minWidth: 220 }}>
              <Icon name="search" /><input placeholder={t('contractsSearch')} value={q} onChange={(e) => setQ(e.target.value)} />
            </div>
            <div className="seg">
              {STATUSES.map((s) => (
                <button key={s} data-on={status === s} onClick={() => { setStatus(s); setPage(0); }}>{statusLabel[s]}</button>
              ))}
            </div>
            <div className="seg">
              <span className="seg-label">{t('risk')}:</span>
              {RISKS.map((r) => (
                <button key={r} className="mono" data-on={risk === r} onClick={() => { setRisk(r); setPage(0); }}>{r || t('all')}</button>
              ))}
            </div>
          </div>
        </div>
      </Card>

      <ErrorBox error={error} />
      <Card className="block">
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('contract')}</th><th>{t('clientPhone')}</th><th>{t('product')}</th>
              <th className="num">{t('totalValue')}</th><th className="num">{t('remaining')}</th>
              <th className="num">{t('monthlyPayment')}</th><th>{t('status')}</th><th>{t('scoreRisk')}</th>
            </tr></thead>
            <tbody>
              {data?.content.map((c) => {
                const paidPct = c.installmentTotal > 0 ? (c.paidTotal / c.installmentTotal) * 100 : 0;
                const late = c.status === 'LATE' && c.nextDue ? daysSince(c.nextDue) : 0;
                return (
                  <tr key={c.id} onClick={() => nav(`/contracts/${c.id}`)}>
                    <td data-label={t('contract')}>
                      <div className="col" style={{ gap: 1 }}>
                        <b className="mono" style={{ fontSize: 12 }}>{c.contractNo}</b>
                        <span className="faint" style={{ fontSize: 11 }}>{fmtDate(c.createdAt, lang)}</span>
                      </div>
                    </td>
                    <td className="cell-main">
                      <div className="col" style={{ gap: 1 }}>
                        <b>{c.clientName}</b>
                        <span className="mono faint" style={{ fontSize: 11 }}>{c.clientPhone}</span>
                      </div>
                    </td>
                    <td className="muted" data-label={t('product')}>{c.productName}</td>
                    <td className="num" data-label={t('totalValue')}>{fmtSom(c.installmentTotal, lang)}</td>
                    <td className="num" data-label={t('remaining')}>
                      <div className="col" style={{ gap: 4, alignItems: 'flex-end' }}>
                        <b>{fmtMln(c.remaining, lang)}</b>
                        <div className="bar mini-bar" title={`${Math.round(paidPct)}%`}><i style={{ width: `${paidPct}%` }} /></div>
                      </div>
                    </td>
                    <td className="num" data-label={t('monthlyPayment')}>{fmtSom(c.monthlyPayment, lang)}</td>
                    <td data-label={t('status')}>
                      <div className="col" style={{ gap: 3, alignItems: 'flex-start' }}>
                        <ContractStatusBadge status={c.status} />
                        {late > 0 && <span className="mono" style={{ fontSize: 10.5, color: 'var(--danger)' }}>{t('daysLateN', { n: late })}</span>}
                      </div>
                    </td>
                    <td data-label={t('scoreRisk')}>
                      <div className="row" style={{ gap: 6 }}>
                        <RiskPill cat={c.riskCategory} />
                        {c.scoreTotal != null && <span className="mono faint" style={{ fontSize: 11.5 }}>{c.scoreTotal}</span>}
                      </div>
                    </td>
                  </tr>
                );
              })}
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
    </div>
  );
}
