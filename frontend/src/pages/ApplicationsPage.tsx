import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { ApplicationStatus, ApplicationView, Page } from '../api/types';
import { Badge, Btn, Card, Empty, ErrorBox, Field, Icon, Modal, RiskPill, Stat, type Tone } from '../components/ui';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { fmtDateTime, fmtSom } from '../lib/format';

const STATUSES: (ApplicationStatus | '')[] = ['', 'PENDING', 'APPROVED', 'CONTRACTED', 'REJECTED'];
const TONE: Record<ApplicationStatus, Tone> = { PENDING: 'warn', APPROVED: 'info', CONTRACTED: 'success', REJECTED: 'danger' };

export function ApplicationBadge({ status }: { status: ApplicationStatus }) {
  const { t } = useTranslation();
  return <Badge tone={TONE[status]} dot>{t(`ap_${status}`)}</Badge>;
}

export default function ApplicationsPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const qc = useQueryClient();
  const { can } = useAuth();
  const branch = useBranch();
  const [params, setParams] = useSearchParams();
  const [status, setStatus] = useState<ApplicationStatus | ''>('');
  const [q, setQ] = useState('');
  const [debounced, setDebounced] = useState('');
  const [page, setPage] = useState(0);
  const [rejecting, setRejecting] = useState<ApplicationView | null>(null);
  const [reason, setReason] = useState('');
  const [open, setOpen] = useState<ApplicationView | null>(null);

  useEffect(() => {
    const h = setTimeout(() => { setDebounced(q.trim()); setPage(0); }, 300);
    return () => clearTimeout(h);
  }, [q]);

  const filter = { status, q: debounced };
  const list = useQuery({
    queryKey: ['applications', filter, page, branch.param],
    queryFn: () => api.get<Page<ApplicationView>>(`/api/applications${qs({ ...filter, page, branchId: branch.param })}`),
  });
  const counts = useQuery({
    queryKey: ['applications-counts', branch.param],
    queryFn: () => api.get<Record<ApplicationStatus, number>>(`/api/applications/counts${qs({ branchId: branch.param })}`),
  });

  // Ariza yaratilgandan keyin shu yerga ?focus=ID bilan o'tiladi
  const focus = params.get('focus');
  useEffect(() => {
    if (!focus) return;
    api.get<ApplicationView>(`/api/applications/${focus}`).then(setOpen).catch(() => undefined);
  }, [focus]);

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['applications'] });
    void qc.invalidateQueries({ queryKey: ['applications-counts'] });
    void qc.invalidateQueries({ queryKey: ['contracts'] });
  };
  const approve = useMutation({
    mutationFn: (id: number) => api.post<ApplicationView>(`/api/applications/${id}/approve`),
    onSuccess: (a) => { setOpen(a); refresh(); },
  });
  const reject = useMutation({
    mutationFn: (id: number) => api.post<ApplicationView>(`/api/applications/${id}/reject`, { reason: reason || undefined }),
    onSuccess: (a) => { setRejecting(null); setReason(''); setOpen(a); refresh(); },
  });
  const toContract = useMutation({
    mutationFn: (id: number) => api.post<ApplicationView>(`/api/applications/${id}/contract`),
    onSuccess: (a) => { refresh(); if (a.contractId) nav(`/contracts/${a.contractId}`); },
  });

  const c = counts.data;
  const closeDetail = () => { setOpen(null); if (focus) { params.delete('focus'); setParams(params, { replace: true }); } };

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="doc" size={18} /></span>{t('applications')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('applicationsSub')}</span>
        </div>
        {can('CONTRACTS_CREATE') && <Btn icon="plus" size="sm" onClick={() => nav('/new-credit')}>{t('newCredit')}</Btn>}
      </div>

      <div className="kpi4">
        <Stat icon="clock" tone="gold" label={t('ap_PENDING')} value={c?.PENDING ?? '—'} sub={t('ap_PENDING_sub')} />
        <Stat icon="check" tone="info" label={t('ap_APPROVED')} value={c?.APPROVED ?? '—'} sub={t('ap_APPROVED_sub')} />
        <Stat icon="contracts" tone="success" label={t('ap_CONTRACTED')} value={c?.CONTRACTED ?? '—'} sub={t('ap_CONTRACTED_sub')} />
        <Stat icon="x" tone="danger" label={t('ap_REJECTED')} value={c?.REJECTED ?? '—'} sub={t('ap_REJECTED_sub')} />
      </div>

      <Card className="block">
        <div className="card-pad filter-bar">
          <div className="search" style={{ flex: 1, minWidth: 220 }}>
            <Icon name="search" /><input placeholder={t('applicationsSearch')} value={q} onChange={(e) => setQ(e.target.value)} />
          </div>
          <div className="seg">
            {STATUSES.map((s) => (
              <button key={s} data-on={status === s} onClick={() => { setStatus(s); setPage(0); }}>
                {s ? t(`ap_${s}`) : t('all')}
              </button>
            ))}
          </div>
        </div>
        <ErrorBox error={list.error ?? approve.error ?? toContract.error} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('application')}</th><th>{t('clientPhone')}</th><th>{t('product')}</th>
              <th className="num">{t('installmentTotalShort')}</th><th className="num">{t('monthlyPayment')}</th>
              <th>{t('status')}</th><th>{t('scoreRisk')}</th><th></th>
            </tr></thead>
            <tbody>
              {list.data?.content.map((a) => (
                <tr key={a.id} onClick={() => setOpen(a)}>
                  <td data-label={t('application')}>
                    <div className="col" style={{ gap: 1 }}>
                      <b className="mono" style={{ fontSize: 12 }}>{a.applicationNo}</b>
                      <span className="faint" style={{ fontSize: 11 }}>{fmtDateTime(a.createdAt, lang)}</span>
                    </div>
                  </td>
                  <td className="cell-main">
                    <div className="col" style={{ gap: 1 }}>
                      <b>{a.clientName}</b>
                      <span className="mono faint" style={{ fontSize: 11 }}>{a.clientPhone}</span>
                    </div>
                  </td>
                  <td className="muted" data-label={t('product')}>{a.itemsSummary}</td>
                  <td className="num" data-label={t('installmentTotalShort')}>{fmtSom(a.installmentTotal, lang)}</td>
                  <td className="num" data-label={t('monthlyPayment')}>{fmtSom(a.monthlyPayment, lang)} × {a.termMonths}</td>
                  <td data-label={t('status')}><ApplicationBadge status={a.status} /></td>
                  <td data-label={t('scoreRisk')}>
                    <div className="row" style={{ gap: 6 }}>
                      <RiskPill cat={a.riskCategory} />
                      {a.scoreTotal != null && <span className="mono faint" style={{ fontSize: 11.5 }}>{a.scoreTotal}</span>}
                    </div>
                  </td>
                  <td onClick={(e) => e.stopPropagation()}>
                    <ActionButtons a={a} can={can} busy={approve.isPending || toContract.isPending}
                      onApprove={() => approve.mutate(a.id)} onReject={() => setRejecting(a)}
                      onContract={() => toContract.mutate(a.id)} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {list.data && list.data.content.length === 0 && <Empty>{t('noResults')}</Empty>}
        {list.data && list.data.totalPages > 1 && (
          <div className="pager">
            <Btn variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}><Icon name="chevL" size={16} /></Btn>
            <span className="faint mono">{page + 1} / {list.data.totalPages}</span>
            <Btn variant="ghost" size="sm" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage(page + 1)}><Icon name="chevR" size={16} /></Btn>
          </div>
        )}
      </Card>

      {open && (
        <Modal title={`${t('application')} · ${open.applicationNo}`} onClose={closeDetail} wide
          footer={<ActionButtons a={open} can={can} busy={approve.isPending || toContract.isPending} full
            onApprove={() => approve.mutate(open.id)} onReject={() => setRejecting(open)}
            onContract={() => toContract.mutate(open.id)} />}>
          <div className="col" style={{ gap: 14 }}>
            <div className="row between">
              <b>{open.clientName}</b><ApplicationBadge status={open.status} />
            </div>
            {open.status === 'REJECTED' && open.rejectionReason && (
              <div className="stage-tip st-danger" style={{ marginTop: 0 }}><Icon name="alert" size={18} /><span>{open.rejectionReason}</span></div>
            )}
            {open.status === 'PENDING' && (
              <div className="stage-tip st-warn" style={{ marginTop: 0 }}><Icon name="clock" size={18} /><span>{t('ap_PENDING_hint')}</span></div>
            )}
            <dl className="kv">
              <dt>{t('product')}</dt><dd>{open.itemsSummary}</dd>
              <dt>{t('productPrice')}</dt><dd>{fmtSom(open.costPrice, lang)}</dd>
              <dt>{t('downPayment')}</dt><dd>{fmtSom(open.downPayment, lang)}</dd>
              <dt>{t('markup')}</dt><dd>{open.markupPct}%</dd>
              <dt>{t('installmentTotal')}</dt><dd>{fmtSom(open.installmentTotal, lang)}</dd>
              <dt>{t('monthlyPayment')}</dt><dd>{fmtSom(open.monthlyPayment, lang)} × {open.termMonths}</dd>
              <dt>{t('risk')}</dt><dd><RiskPill cat={open.riskCategory} /> {open.scoreTotal}</dd>
              {!!open.scoreDetails?.stopFactors?.length && (
                <><dt>{t('stopTitle')}</dt><dd>{open.scoreDetails.stopFactors.map((s) => t(`s_${s}`)).join(', ')}</dd></>
              )}
              {open.officerName && <><dt>{t('officer')}</dt><dd>{open.officerName}</dd></>}
              {open.branchName && <><dt>{t('branch')}</dt><dd>{open.branchName}</dd></>}
              {open.decidedByName && <><dt>{t('decidedBy')}</dt><dd>{open.decidedByName} · {fmtDateTime(open.decidedAt, lang)}</dd></>}
              {open.contractNo && <><dt>{t('contract')}</dt><dd><Link to={`/contracts/${open.contractId}`}>{open.contractNo}</Link></dd></>}
            </dl>
          </div>
        </Modal>
      )}

      {rejecting && (
        <Modal title={`${t('rejectApplication')} · ${rejecting.applicationNo}`} onClose={() => setRejecting(null)}
          footer={<>
            <Btn variant="ghost" onClick={() => setRejecting(null)}>{t('cancel')}</Btn>
            <Btn variant="danger" icon="x" disabled={reject.isPending} onClick={() => reject.mutate(rejecting.id)}>{t('rejectApplication')}</Btn>
          </>}>
          <div className="col" style={{ gap: 12 }}>
            <ErrorBox error={reject.error} />
            <Field label={t('rejectReason')}>
              <textarea className="input" rows={3} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
            </Field>
          </div>
        </Modal>
      )}
    </div>
  );
}

function ActionButtons({ a, can, busy, onApprove, onReject, onContract, full }: {
  a: ApplicationView; can: (...p: import('../api/types').Permission[]) => boolean; busy: boolean; full?: boolean;
  onApprove: () => void; onReject: () => void; onContract: () => void;
}) {
  const { t } = useTranslation();
  const canDecide = can('CONTRACTS_APPROVE');
  const sz = full ? undefined : 'sm';
  return (
    <div className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
      {a.status === 'PENDING' && canDecide && <Btn size={sz} icon="check" disabled={busy} onClick={onApprove}>{t('approve')}</Btn>}
      {(a.status === 'PENDING' || a.status === 'APPROVED') && canDecide && (
        <Btn size={sz} variant="ghost" icon="x" onClick={onReject}>{t('reject')}</Btn>
      )}
      {a.status === 'APPROVED' && can('CONTRACTS_CREATE') && (
        <Btn size={sz} variant="gold" icon="contracts" disabled={busy} onClick={onContract}>{t('makeContract')}</Btn>
      )}
    </div>
  );
}
