import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, ApiError, qs } from '../api/client';
import type { Branch, BranchStats } from '../api/types';
import { Badge, BarsChart, Btn, Card, CardHead, Empty, ErrorBox, Field, Icon, Loading, Modal } from '../components/ui';
import { Progress } from '../components/viz';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { currentMonth, fmtMln, fmtMonth } from '../lib/format';

export default function BranchesPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const branch = useBranch();
  const [month, setMonth] = useState(currentMonth);
  const [edit, setEdit] = useState<Branch | 'new' | null>(null);
  const { data, isLoading, error } = useQuery({
    queryKey: ['branch-stats', month],
    queryFn: () => api.get<BranchStats[]>(`/api/branches/stats${qs({ month })}`),
  });
  if (isLoading) return <Loading />;
  if (error || !data) return <div className="page"><ErrorBox error={error} /></div>;

  const active = data.filter((b) => b.active);
  const sum = (k: keyof BranchStats) => active.reduce((a, b) => a + (Number(b[k]) || 0), 0);
  const chart = active.map((b) => ({ m: b.code, debt: b.debt / 1e6, overdue: b.overdue / 1e6, issued: b.issued / 1e6 }));
  const mlnTick = (n: number) => (Number.isInteger(n) ? String(n) : n.toFixed(1).replace('.', ','));

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="building" size={18} /></span>{t('branchesTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>
            {t('branchesSub')} · {fmtMonth(month, lang)}
          </span>
        </div>
        <div className="row" style={{ gap: 8 }}>
          <input className="input" type="month" value={month} max={currentMonth()} style={{ width: 160 }}
            onChange={(e) => e.target.value && setMonth(e.target.value)} aria-label={t('month')} />
          {can('BRANCHES_MANAGE') && <Btn size="sm" icon="plus" onClick={() => setEdit('new')}>{t('addBranch')}</Btn>}
        </div>
      </div>

      {active.length > 1 && (
        <div className="kpi4">
          <div className="ministat anim" data-tone="primary"><span className="stat-ic sm"><Icon name="building" size={18} /></span>
            <span className="ministat-label">{t('branchesActive')}</span><b className="ministat-val tnum">{active.length}</b></div>
          <div className="ministat anim" data-tone="success"><span className="stat-ic sm"><Icon name="wallet" size={18} /></span>
            <span className="ministat-label">{t('totalDebtLong')}</span><b className="ministat-val tnum">{fmtMln(sum('debt'), lang)}</b></div>
          <div className="ministat anim" data-tone="gold"><span className="stat-ic sm"><Icon name="newcredit" size={18} /></span>
            <span className="ministat-label">{t('issuedThisMonth')}</span><b className="ministat-val tnum">{fmtMln(sum('issued'), lang)}</b></div>
          <div className="ministat anim" data-tone="danger"><span className="stat-ic sm"><Icon name="alert" size={18} /></span>
            <span className="ministat-label">{t('overdue')}</span><b className="ministat-val tnum">{fmtMln(sum('overdue'), lang)}</b></div>
        </div>
      )}

      <div className="branch-grid block">
        {data.map((b) => {
          const overdueShare = b.debt > 0 ? (b.overdue / b.debt) * 100 : 0;
          const selected = branch.selected === b.id;
          return (
            <Card key={b.id} className={`branch-card${b.active ? '' : ' is-off'}${selected ? ' is-sel' : ''}`}>
              <div className="bc-head">
                <span className="bc-ic"><Icon name="building" size={20} /></span>
                <div className="col" style={{ gap: 2, minWidth: 0, flex: 1 }}>
                  <div className="row" style={{ gap: 8 }}>
                    <b className="ellipsis" style={{ fontSize: 15 }}>{b.name}</b>
                    <span className="chip-sm mono">{b.code}</span>
                    {!b.active && <Badge tone="neutral">{t('inactive')}</Badge>}
                  </div>
                  <span className="faint ellipsis" style={{ fontSize: 12 }}>
                    {[b.managerName, b.phone].filter(Boolean).join(' · ') || '—'}
                  </span>
                </div>
                {can('BRANCHES_MANAGE') && (
                  <button className="iconbtn" style={{ width: 32, height: 32 }} title={t('edit')} onClick={() => setEdit(b)}>
                    <Icon name="pen" size={15} />
                  </button>
                )}
              </div>
              {b.address && <div className="bc-addr faint"><Icon name="pin" size={13} />{b.address}</div>}

              <div className="bc-stats">
                <div><span>{t('activeContracts')}</span><b className="mono">{b.openContracts}</b></div>
                <div><span>{t('totalDebtLong')}</span><b className="mono">{fmtMln(b.debt, lang)}</b></div>
                <div><span>{t('overdue')}</span><b className="mono" style={{ color: b.overdue > 0 ? 'var(--danger)' : undefined }}>{fmtMln(b.overdue, lang)}</b></div>
                <div><span>{t('collectionRate')}</span><b className="mono">{b.collectionRate != null ? `${b.collectionRate}%` : '—'}</b></div>
                <div><span>{t('collectedThisMonth')}</span><b className="mono">{fmtMln(b.collected, lang)}</b></div>
                <div><span>{t('staff')}</span><b className="mono">{b.staff}</b></div>
              </div>

              <div className="col" style={{ gap: 6 }}>
                <div className="row between" style={{ fontSize: 12 }}>
                  <span className="faint">{t('salesPlan')} · {b.issuedCount} {t('pcs')}</span>
                  <span className="mono">
                    {fmtMln(b.issued, lang)}{b.monthlyTarget ? <span className="faint"> / {fmtMln(b.monthlyTarget, lang)}</span> : ''}
                    {b.planPct != null && <b style={{ marginLeft: 6 }}>{Math.round(b.planPct)}%</b>}
                  </span>
                </div>
                <Progress pct={b.planPct ?? 0} tone={b.planPct == null ? 'var(--surface-3)' : undefined} />
                <div className="row between" style={{ fontSize: 12 }}>
                  <span className="faint">{t('overdueShare')}</span>
                  <span className="mono" style={{ color: overdueShare > 10 ? 'var(--danger)' : 'var(--text-muted)' }}>{overdueShare.toFixed(1).replace('.', ',')}%</span>
                </div>
              </div>

              {branch.canSwitch && b.active && (
                <button className="link-btn" style={{ alignSelf: 'flex-start' }}
                  onClick={() => branch.setSelected(selected ? null : b.id)}>
                  {selected ? t('showAllBranches') : t('workInBranch')}
                </button>
              )}
            </Card>
          );
        })}
        {data.length === 0 && <Empty />}
      </div>

      {active.length > 1 && (
        <Card className="block">
          <CardHead title={t('branchCompare')} sub={lang === 'ru' ? 'млн сум' : "mln so'm"}
            right={<>
              <span className="legend"><i style={{ background: 'var(--primary)' }} />{t('totalDebtLong')}</span>
              <span className="legend"><i style={{ background: 'var(--gold)' }} />{t('issuedThisMonth')}</span>
              <span className="legend"><i style={{ background: 'var(--danger)' }} />{t('overdue')}</span>
            </>} />
          <div className="card-pad">
            <BarsChart data={chart} height={220} fmt={mlnTick} keys={[
              { key: 'debt', label: t('totalDebtLong'), color: 'var(--primary)' },
              { key: 'issued', label: t('issuedThisMonth'), color: 'var(--gold)' },
              { key: 'overdue', label: t('overdue'), color: 'var(--danger)' },
            ]} />
          </div>
        </Card>
      )}

      {edit && <BranchModal branch={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </div>
  );
}

function BranchModal({ branch, onClose }: { branch: Branch | null; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [d, setD] = useState<Omit<Branch, 'id'>>(branch ?? { code: '', name: '', active: true });
  const save = useMutation({
    mutationFn: () => (branch ? api.put(`/api/branches/${branch.id}`, d) : api.post('/api/branches', d)),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['branch-stats'] });
      void qc.invalidateQueries({ queryKey: ['branches'] });
      onClose();
    },
  });
  const fe = save.error instanceof ApiError ? save.error.fields ?? {} : {};
  const text = (k: 'code' | 'name' | 'address' | 'phone' | 'managerName', label: string, req = false) => (
    <Field label={label} req={req} err={fe[k]}>
      <input className="input" value={d[k] ?? ''} onChange={(e) => setD({ ...d, [k]: e.target.value })} />
    </Field>
  );
  return (
    <Modal title={branch ? t('editBranch') : t('addBranch')} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending || !d.code || !d.name} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        {save.error && !Object.keys(fe).length && <ErrorBox error={save.error} />}
        <div className="form-grid">
          {text('name', t('branchName'), true)}
          {text('code', t('branchCode'), true)}
          {text('managerName', t('branchManager'))}
          {text('phone', t('phone'))}
        </div>
        {text('address', t('address'))}
        <Field label={t('salesPlanMonthly')} hint={t('salesPlanHint')} err={fe.monthlyTarget}>
          <input className="input mono" type="number" min={0} value={d.monthlyTarget ?? ''}
            onChange={(e) => setD({ ...d, monthlyTarget: e.target.value ? Number(e.target.value) : undefined })} />
        </Field>
        <label className="check-row"><input type="checkbox" checked={d.active} onChange={(e) => setD({ ...d, active: e.target.checked })} />{t('active')}</label>
      </div>
    </Modal>
  );
}
