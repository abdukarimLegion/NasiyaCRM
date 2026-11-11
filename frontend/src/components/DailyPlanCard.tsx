import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { DailyMonitor, DailyPlan } from '../api/types';
import { useAuth } from '../lib/auth';
import { fmtNum, fmtSom } from '../lib/format';
import { Badge, Btn, Card, CardHead, ErrorBox, Field, Icon, Modal } from './ui';

/**
 * Kunlik reja va bajarilgan ishlar monitoringi: nasiya savdo hajmi, shartnomalar soni va kassa tushumi.
 * Bajarilish ish kunining o'tgan qismi bilan solishtiriladi (ortda / rejada).
 */
export default function DailyPlanCard({ branchId }: { branchId?: number | null }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const [editing, setEditing] = useState(false);
  const q = useQuery({
    queryKey: ['daily-plan', branchId ?? null],
    queryFn: () => api.get<DailyMonitor>(`/api/dashboard/daily${qs({ branchId })}`),
    refetchInterval: 60_000,
  });
  const d = q.data;
  if (!d) return q.error ? <ErrorBox error={q.error} /> : null;

  const rows: { key: keyof DailyPlan; money: boolean }[] = [
    { key: 'sales', money: true }, { key: 'contracts', money: false }, { key: 'collection', money: true },
  ];
  const fmt = (v: number, money: boolean) => (money ? fmtSom(v, lang) : `${fmtNum(v)} ${t('pcs')}`);
  const pcts = rows.filter((r) => d.plan[r.key] > 0).map((r) => Math.min(100, (d.actual[r.key] / d.plan[r.key]) * 100));
  const overall = pcts.length ? Math.round(pcts.reduce((a, b) => a + b, 0) / pcts.length) : null;

  return (
    <Card className="block">
      <CardHead
        title={<><Icon name="target" size={16} /> {t('dailyTitle')} {overall != null && <Badge tone={overall >= d.dayProgressPct ? 'success' : 'warn'}>{overall}%</Badge>}</>}
        sub={t('dailySub')}
        right={can('SETTINGS_EDIT') && <Btn size="sm" variant="soft" icon="settings" onClick={() => setEditing(true)}>{t('dailyEdit')}</Btn>} />
      <div className="card-pad daily-grid">
        {rows.map((r) => {
          const plan = d.plan[r.key];
          const actual = d.actual[r.key];
          const p = plan > 0 ? Math.min(100, (actual / plan) * 100) : 0;
          const behind = plan > 0 && p < d.dayProgressPct;
          return (
            <div key={r.key} className="col" style={{ gap: 6, minWidth: 0 }}>
              <span className="faint" style={{ fontSize: 12 }}>{t(`daily_${r.key}`)}</span>
              <b className="mono" style={{ fontSize: 17 }}>{fmt(actual, r.money)}</b>
              <div className="daily-bar" title={`${Math.round(p)}%`}>
                <i style={{ width: `${p}%`, background: plan <= 0 ? 'var(--surface-3)' : behind ? 'var(--gold)' : 'var(--success)' }} />
                {plan > 0 && <span className="daily-mark" style={{ left: `${d.dayProgressPct}%` }} title={t('dailyNow')} />}
              </div>
              <div className="row between" style={{ fontSize: 11.5 }}>
                <span className="faint">{t('dailyPlan')}: {plan > 0 ? fmt(plan, r.money) : t('dailyNoPlan')}</span>
                {plan > 0 && <b style={{ color: behind ? 'var(--gold)' : 'var(--success)' }}>{Math.round(p)}%</b>}
              </div>
              {plan > 0 && (
                <span className="faint" style={{ fontSize: 11.5 }}>
                  {actual >= plan ? t('dailyDone') : `${t('dailyLeft')}: ${fmt(plan - actual, r.money)}`}
                </span>
              )}
            </div>
          );
        })}
      </div>
      {editing && <PlanModal plan={d.plan} branchId={branchId} onClose={() => setEditing(false)} />}
    </Card>
  );
}

function PlanModal({ plan, branchId, onClose }: { plan: DailyPlan; branchId?: number | null; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [f, setF] = useState<DailyPlan>(plan);
  const save = useMutation({
    mutationFn: () => api.put('/api/dashboard/daily', { ...f, branchId: branchId ?? undefined }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['daily-plan'] }); onClose(); },
  });
  const num = (k: keyof DailyPlan) => (
    <Field label={t(`dailySet_${k}`)}>
      <input className="input mono" type="number" min={0} value={f[k]} onChange={(e) => setF((s) => ({ ...s, [k]: Math.max(0, Number(e.target.value)) }))} />
    </Field>
  );
  return (
    <Modal title={t('dailyEditTitle')} onClose={onClose}
      footer={<><Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn><Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn></>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <span className="faint" style={{ fontSize: 12 }}>{t(branchId ? 'dailyScopeBranch' : 'dailyScopeAll')}</span>
        {num('sales')}{num('contracts')}{num('collection')}
      </div>
    </Modal>
  );
}
