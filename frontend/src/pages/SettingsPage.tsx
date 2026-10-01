import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, ApiError } from '../api/client';
import type { Role, Settings, UserView } from '../api/types';
import { Btn, Card, CardHead, ErrorBox, Field, Modal } from '../components/ui';

const ROLES: Role[] = ['ADMIN', 'CREDIT_OFFICER', 'COLLECTOR', 'CASHIER'];

export default function SettingsPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const settings = useQuery({ queryKey: ['settings'], queryFn: () => api.get<Settings>('/api/settings') });
  const [d, setD] = useState<Settings | null>(null);
  useEffect(() => {
    if (settings.data) setD(settings.data);
  }, [settings.data]);
  const save = useMutation({
    mutationFn: () => api.put<Settings>('/api/settings', d),
    onSuccess: (s) => qc.setQueryData(['settings'], s),
  });
  const fe = save.error instanceof ApiError ? save.error.fields ?? {} : {};
  if (!d) return <div className="page"><ErrorBox error={settings.error} /></div>;
  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => setD({ ...d, [k]: v });
  const text = (k: keyof Settings, label: string, type = 'text') => (
    <Field label={label} err={fe[k]}>
      <input className="input" type={type} value={(d[k] as string | number | undefined) ?? ''}
        onChange={(e) => set(k, (type === 'number' ? Number(e.target.value) : e.target.value) as never)} />
    </Field>
  );

  return (
    <div className="page col" style={{ gap: 22, maxWidth: 980 }}>
      <Card>
        <CardHead title={t('settings')} right={
          <Btn size="sm" icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{save.isSuccess ? t('saved') : t('save')}</Btn>
        } />
        <div className="card-pad col" style={{ gap: 14 }}>
          {save.error && !Object.keys(fe).length && <ErrorBox error={save.error} />}
          <div className="form-grid">
            {text('companyName', t('companyName'))}
            {text('brandName', t('brandName'))}
            <Field label={t('primaryColor')} err={fe.primaryColor}>
              <div className="row gap-sm">
                <input type="color" value={d.primaryColor} onChange={(e) => set('primaryColor', e.target.value)} />
                <input className="input" value={d.primaryColor} onChange={(e) => set('primaryColor', e.target.value)} />
              </div>
            </Field>
            {text('logoUrl', t('logoUrl'))}
            {text('contractPrefix', t('contractPrefix'))}
            <Field label={t('defaultLang')}>
              <select className="select" value={d.defaultLang} onChange={(e) => set('defaultLang', e.target.value as 'uz' | 'ru')}>
                <option value="uz">O'zbekcha</option><option value="ru">Русский</option>
              </select>
            </Field>
            {text('roundingStep', t('roundingStep'), 'number')}
            {text('maxActiveContracts', t('maxActiveContracts'), 'number')}
            {text('smsSender', t('smsSender'))}
          </div>
        </div>
      </Card>
      <UsersCard />
    </div>
  );
}

function UsersCard() {
  const { t } = useTranslation();
  const [edit, setEdit] = useState<UserView | 'new' | null>(null);
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.get<UserView[]>('/api/users') });
  return (
    <Card>
      <CardHead title={t('users')} right={<Btn size="sm" icon="plus" onClick={() => setEdit('new')}>{t('addUser')}</Btn>} />
      <div className="table-wrap">
        <table className="tbl">
          <thead><tr><th>{t('fio')}</th><th>{t('username')}</th><th>{t('role')}</th></tr></thead>
          <tbody>
            {users.data?.map((u) => (
              <tr key={u.id} onClick={() => setEdit(u)}>
                <td><b>{u.fullName}</b></td><td className="mono">{u.username}</td><td>{t(`r_${u.role}`)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {edit && <UserModal user={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </Card>
  );
}

function UserModal({ user, onClose }: { user: UserView | null; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [username, setUsername] = useState(user?.username ?? '');
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [role, setRole] = useState<Role>(user?.role ?? 'CREDIT_OFFICER');
  const [active, setActive] = useState(true);
  const [password, setPassword] = useState('');
  const save = useMutation({
    mutationFn: () => (user
      ? api.put(`/api/users/${user.id}`, { fullName, role, active, newPassword: password || undefined })
      : api.post('/api/users', { username, password, fullName, role })),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['users'] });
      onClose();
    },
  });
  return (
    <Modal title={user ? t('edit') : t('addUser')} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        {!user && <Field label={t('username')} req><input className="input" value={username} onChange={(e) => setUsername(e.target.value)} /></Field>}
        <Field label={t('fio')} req><input className="input" value={fullName} onChange={(e) => setFullName(e.target.value)} /></Field>
        <Field label={t('role')}>
          <select className="select" value={role} onChange={(e) => setRole(e.target.value as Role)}>
            {ROLES.map((r) => <option key={r} value={r}>{t(`r_${r}`)}</option>)}
          </select>
        </Field>
        <Field label={user ? t('newPassword') : t('password')} req={!user} hint="min 8">
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </Field>
        {user && <label className="check-row"><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />{t('active')}</label>}
      </div>
    </Modal>
  );
}
