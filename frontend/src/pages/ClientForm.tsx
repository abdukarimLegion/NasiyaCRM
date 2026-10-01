import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, ApiError } from '../api/client';
import type { Client, ClientRequest } from '../api/types';
import { Btn, ErrorBox, Field, Modal } from '../components/ui';

const EMPTY: ClientRequest = {
  fullName: '', pinfl: '', phone: '+998', blacklisted: false,
};

export default function ClientForm({ initial, onClose, onSaved }: {
  initial?: Client; onClose: () => void; onSaved?: (c: Client) => void;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [f, setF] = useState<ClientRequest>(() => (initial ? { ...initial } : { ...EMPTY }));
  const set = <K extends keyof ClientRequest>(k: K, v: ClientRequest[K]) => setF((s) => ({ ...s, [k]: v }));

  const save = useMutation({
    mutationFn: () => {
      const body = { ...f, monthlyIncome: f.monthlyIncome ? Number(f.monthlyIncome) : undefined };
      return initial ? api.put<Client>(`/api/clients/${initial.id}`, body) : api.post<Client>('/api/clients', body);
    },
    onSuccess: (c) => {
      void qc.invalidateQueries({ queryKey: ['clients'] });
      void qc.invalidateQueries({ queryKey: ['client', c.id] });
      onSaved?.(c);
      onClose();
    },
  });
  const fe = save.error instanceof ApiError ? save.error.fields ?? {} : {};

  const text = (k: keyof ClientRequest, label: string, opts: { req?: boolean; type?: string } = {}) => (
    <Field label={label} req={opts.req} err={fe[k]}>
      <input className="input" type={opts.type ?? 'text'} data-err={!!fe[k]}
        value={(f[k] as string | number | undefined) ?? ''} onChange={(e) => set(k, e.target.value as never)} />
    </Field>
  );

  return (
    <Modal wide title={initial ? t('edit') : t('addClient')} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        {save.error && !Object.keys(fe).length && <ErrorBox error={save.error} />}
        <div className="form-grid">
          {text('fullName', t('fio'), { req: true })}
          {text('pinfl', t('pinfl'), { req: true })}
          {text('phone', t('phone'), { req: true })}
          {text('extraPhone', t('phone2'))}
          {text('passportSeries', t('passport'))}
          {text('passportExpiry', `${t('passport')} · muddati`, { type: 'date' })}
          {text('birthDate', t('birth'), { type: 'date' })}
          {text('monthlyIncome', t('income'), { type: 'number' })}
          {text('region', t('region'))}
          {text('district', t('district'))}
          {text('address', t('address'))}
          {text('workplace', t('job'))}
          {text('familyStatus', t('family'))}
        </div>
        <label className="check-row">
          <input type="checkbox" checked={f.blacklisted} onChange={(e) => set('blacklisted', e.target.checked)} />
          {t('s_BLACKLIST')}
        </label>
        <Field label={t('note')}>
          <textarea className="input" rows={2} value={f.note ?? ''} onChange={(e) => set('note', e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}
