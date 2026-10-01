import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { Channel, NotificationLogDto, NotificationTemplate, Page } from '../api/types';
import { Badge, Btn, Card, CardHead, Empty, ErrorBox, Field, Modal, Seg } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtDate } from '../lib/format';

export default function NotificationsPage() {
  const { t, i18n } = useTranslation();
  const { hasRole } = useAuth();
  const qc = useQueryClient();
  const [edit, setEdit] = useState<NotificationTemplate | null>(null);
  const templates = useQuery({ queryKey: ['templates'], queryFn: () => api.get<NotificationTemplate[]>('/api/notifications/templates') });
  const log = useQuery({ queryKey: ['notify-log'], queryFn: () => api.get<Page<NotificationLogDto>>('/api/notifications/log?size=50') });
  const run = useMutation({
    mutationFn: () => api.post<{ sent: number; failed: number; skipped: number }>('/api/notifications/run-reminders'),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['notify-log'] }),
  });
  const chLabel: Record<Channel, string> = { SMS: t('chSms'), TELEGRAM: t('chTelegram'), BOTH: t('chBoth') };

  return (
    <div className="page col" style={{ gap: 22 }}>
      <Card>
        <CardHead title={t('notifications')} sub={t('notifyDesc')}
          right={hasRole('ADMIN') && (
            <Btn size="sm" variant="soft" icon="message" disabled={run.isPending} onClick={() => run.mutate()}>{t('runReminders')}</Btn>
          )} />
        {run.data && <div className="card-pad faint">SENT {run.data.sent} · FAILED {run.data.failed} · SKIPPED {run.data.skipped}</div>}
        <ErrorBox error={templates.error ?? run.error} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr><th>{t('event')}</th><th>{t('channel')}</th><th>{t('template')}</th><th>{t('status')}</th></tr></thead>
            <tbody>
              {templates.data?.map((tp) => (
                <tr key={tp.id} onClick={() => hasRole('ADMIN') && setEdit(tp)}>
                  <td className="cell-main"><b>{t(`e_${tp.eventKey}`)}</b></td>
                  <td data-label={t('channel')}>{chLabel[tp.channel]}</td>
                  <td className="muted" data-label={t('template')} style={{ maxWidth: 420 }}>{i18n.language === 'ru' ? tp.textRu : tp.textUz}</td>
                  <td data-label={t('status')}>{tp.enabled ? <Badge tone="success" dot>{t('enabled')}</Badge> : <Badge>{t('disabled')}</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
      <Card>
        <CardHead title={t('log')} />
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr><th>{t('date')}</th><th>{t('event')}</th><th>{t('channel')}</th><th>{t('phone')}</th><th>{t('template')}</th><th>{t('status')}</th></tr></thead>
            <tbody>
              {log.data?.content.map((l) => (
                <tr key={l.id} style={{ cursor: 'default' }}>
                  <td data-label={t('date')}>{fmtDate(l.createdAt, i18n.language)}</td>
                  <td className="cell-main"><b>{t(`e_${l.eventKey}`)}</b></td>
                  <td data-label={t('channel')}>{chLabel[l.channel]}</td>
                  <td className="mono" data-label={t('phone')}>{l.recipient}</td>
                  <td className="muted" data-label={t('template')} style={{ maxWidth: 380 }}>{l.message}</td>
                  <td data-label={t('status')} title={l.error}>
                    <Badge tone={l.status === 'SENT' ? 'success' : l.status === 'FAILED' ? 'danger' : 'neutral'}>{l.status}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {log.data && log.data.content.length === 0 && <Empty />}
      </Card>
      {edit && <TemplateModal tpl={edit} onClose={() => setEdit(null)} />}
    </div>
  );
}

function TemplateModal({ tpl, onClose }: { tpl: NotificationTemplate; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [d, setD] = useState({ channel: tpl.channel, enabled: tpl.enabled, textUz: tpl.textUz, textRu: tpl.textRu });
  const save = useMutation({
    mutationFn: () => api.put(`/api/notifications/templates/${tpl.id}`, d),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['templates'] });
      onClose();
    },
  });
  return (
    <Modal wide title={t(`e_${tpl.eventKey}`)} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('saveTemplate')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <Seg<Channel> value={d.channel} onChange={(v) => setD({ ...d, channel: v })}
          options={[{ value: 'SMS', label: t('chSms') }, { value: 'TELEGRAM', label: t('chTelegram') }, { value: 'BOTH', label: t('chBoth') }]} />
        <label className="check-row"><input type="checkbox" checked={d.enabled} onChange={(e) => setD({ ...d, enabled: e.target.checked })} />{t('enabled')}</label>
        <Field label="O'zbekcha" hint={`${d.textUz.length} ${t('charCount')} · ${t('notifyHint')}`}>
          <textarea className="input" rows={3} maxLength={500} value={d.textUz} onChange={(e) => setD({ ...d, textUz: e.target.value })} />
        </Field>
        <Field label="Русский" hint={`${d.textRu.length} ${t('charCount')}`}>
          <textarea className="input" rows={3} maxLength={500} value={d.textRu} onChange={(e) => setD({ ...d, textRu: e.target.value })} />
        </Field>
      </div>
    </Modal>
  );
}
