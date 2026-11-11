import { useEffect, useMemo, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Navigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, ApiError } from '../api/client';
import type { AdvancedSettings, CustomRole, DocCode, DocTemplate, Partner, Permission, PermissionMatrix, Role, Settings, UserView } from '../api/types';
import { Avatar, Badge, Btn, Card, CardHead, Empty, ErrorBox, Field, Icon, Modal } from '../components/ui';
import { Tabs, type TabItem } from '../components/viz';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { fmtDateTime } from '../lib/format';

const ROLES: Role[] = ['ADMIN', 'CREDIT_OFFICER', 'COLLECTOR', 'CASHIER', 'BRANCH_MANAGER', 'RISK_ANALYST', 'LEGAL_OFFICER'];
const SECTIONS = ['dashboard', 'clients', 'contracts', 'payments', 'products', 'collection', 'notifications', 'analytics', 'branches', 'system'];

export default function SettingsPage() {
  const { t } = useTranslation();
  const { can } = useAuth();
  const { tab } = useParams();
  const items: TabItem[] = [
    ...(can('SETTINGS_EDIT') ? [{ key: 'general', to: '/settings', label: t('settingsGeneral'), icon: 'settings' as const }] : []),
    ...(can('USERS_MANAGE') ? [
      { key: 'users', to: '/settings/users', label: t('users'), icon: 'clients' as const },
      { key: 'roles', to: '/settings/roles', label: t('rolesPerms'), icon: 'key' as const },
    ] : []),
    ...(can('SETTINGS_EDIT') ? [{ key: 'partners', to: '/settings/partners', label: t('partners'), icon: 'globe' as const }] : []),
    ...(can('DOCUMENTS_EDIT') ? [{ key: 'documents', to: '/settings/documents', label: t('docTemplates'), icon: 'doc' as const }] : []),
  ];
  const current = tab ?? 'general';
  if (!items.some((i) => i.key === current)) {
    const first = items[0];
    return first ? <Navigate to={first.to!} replace /> : null;
  }
  return (
    <div className="page col" style={{ gap: 18, maxWidth: 1100 }}>
      <Tabs items={items} />
      {current === 'general' && <><GeneralCard /><AdvancedCard /></>}
      {current === 'users' && <UsersCard />}
      {current === 'roles' && <><RolesCard /><CustomRolesCard /></>}
      {current === 'partners' && <PartnersCard />}
      {current === 'documents' && <DocumentsCard />}
    </div>
  );
}

/* ------------------------------------------------------------------ umumiy */

function GeneralCard() {
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
  if (!d) return <ErrorBox error={settings.error} />;
  const set = <K extends keyof Settings>(k: K, v: Settings[K]) => setD({ ...d, [k]: v });
  const text = (k: keyof Settings, label: string, type = 'text') => (
    <Field label={label} err={fe[k]}>
      <input className="input" type={type} value={(d[k] as string | number | undefined) ?? ''}
        onChange={(e) => set(k, (type === 'number' ? Number(e.target.value) : e.target.value) as never)} />
    </Field>
  );
  return (
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
  );
}

/** Mijoz verifikatsiyasi qoidalari va moliyaviy parametrlar (prognoz, tahlil va simulyator ishlatadi). */
function AdvancedCard() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['settings-advanced'], queryFn: () => api.get<AdvancedSettings>('/api/settings/advanced') });
  const [d, setD] = useState<AdvancedSettings | null>(null);
  useEffect(() => {
    if (q.data) setD(q.data);
  }, [q.data]);
  const save = useMutation({
    mutationFn: () => api.put<AdvancedSettings>('/api/settings/advanced', d),
    onSuccess: (s) => {
      qc.setQueryData(['settings-advanced'], s);
      void qc.invalidateQueries({ queryKey: ['verification'] });
      void qc.invalidateQueries({ queryKey: ['forecast'] });
    },
  });
  if (!d) return <ErrorBox error={q.error} />;
  const set = <K extends keyof AdvancedSettings>(k: K, v: AdvancedSettings[K]) => setD({ ...d, [k]: v });
  const num = (k: keyof AdvancedSettings, label: string, hint?: string) => (
    <Field label={label} hint={hint}>
      <input className="input mono" type="number" min={0} value={d[k] as number} onChange={(e) => set(k, Number(e.target.value) as never)} />
    </Field>
  );
  return (
    <Card>
      <CardHead title={t('advancedTitle')} sub={t('advancedSub')} right={
        <Btn size="sm" icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{save.isSuccess ? t('saved') : t('save')}</Btn>
      } />
      <div className="card-pad col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <b style={{ fontSize: 13 }}>{t('verifyTitle')}</b>
        <div className="form-grid">
          <Field label={t('verifyMode')} hint={t('verifyModeHint')}>
            <select className="select" value={d.verificationMode} onChange={(e) => set('verificationMode', e.target.value as AdvancedSettings['verificationMode'])}>
              {(['NONE', 'OTP', 'BIOMETRIC', 'ANY', 'BOTH'] as const).map((m) => <option key={m} value={m}>{t(`vm_${m}`)}</option>)}
            </select>
          </Field>
          {num('otpExpirySeconds', t('otpExpiry'), t('seconds'))}
          {num('minFaceMatchPct', t('minFaceMatch'), '%')}
        </div>
        <div className="divider" />
        <b style={{ fontSize: 13 }}>{t('mibTitle')}</b>
        <div className="form-grid">
          <Field label={t('mibModeLabel')} hint={t('mibModeHint')}>
            <select className="select" value={d.mibMode} onChange={(e) => set('mibMode', e.target.value as AdvancedSettings['mibMode'])}>
              {(['OFF', 'OPTIONAL', 'REQUIRED'] as const).map((m) => <option key={m} value={m}>{t(`mibModeOpt_${m}`)}</option>)}
            </select>
          </Field>
          {num('mibValidityDays', t('mibValidity'), t('days'))}
        </div>
        <div className="divider" />
        <b style={{ fontSize: 13 }}>{t('financeParams')}</b>
        <div className="form-grid">
          {num('monthlyOpex', t('monthlyOpex'), t('somHint'))}
          {num('ownCapital', t('ownCapital'), t('ownCapitalHint'))}
          {num('fundingCostPct', t('fundingCost'), t('fundingCostHint'))}
        </div>
      </div>
    </Card>
  );
}

/* ------------------------------------------------------------------ hamkorlar */

function PartnersCard() {
  const { t } = useTranslation();
  const [edit, setEdit] = useState<Partner | 'new' | null>(null);
  const list = useQuery({ queryKey: ['partners'], queryFn: () => api.get<Partner[]>('/api/partners') });
  return (
    <Card>
      <CardHead title={t('partners')} sub={t('partnersSub')} right={<Btn size="sm" icon="plus" onClick={() => setEdit('new')}>{t('addPartner')}</Btn>} />
      <ErrorBox error={list.error} />
      <div className="table-wrap">
        <table className="tbl">
          <thead><tr>
            <th>{t('provider')}</th><th className="num">{t('commissionPct')}</th><th className="num">{t('maxTerm')}</th>
            <th className="num">{t('minDownPct')}</th><th className="num">{t('dtiPct')}</th><th>{t('status')}</th>
          </tr></thead>
          <tbody>
            {list.data?.map((p) => (
              <tr key={p.id} onClick={() => setEdit(p)} style={{ opacity: p.active ? 1 : 0.55 }}>
                <td className="cell-main"><b>{p.name}</b> <span className="mono faint">{p.code}</span></td>
                <td className="num mono" data-label={t('commissionPct')}>{p.commissionPct}%</td>
                <td className="num mono" data-label={t('maxTerm')}>{p.maxTerm}</td>
                <td className="num mono" data-label={t('minDownPct')}>{p.minDownPct}%</td>
                <td className="num mono" data-label={t('dtiPct')}>{p.dtiPct}%</td>
                <td data-label={t('status')}>{p.active ? <Badge tone="success" dot>{t('active')}</Badge> : <Badge tone="neutral">{t('inactive')}</Badge>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {edit && <PartnerModal partner={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </Card>
  );
}

function PartnerModal({ partner, onClose }: { partner: Partner | null; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [d, setD] = useState<Omit<Partner, 'id'>>(partner ?? {
    code: '', name: '', commissionPct: 10, maxTerm: 12, minDownPct: 0, dtiPct: 40, active: true,
  });
  const save = useMutation({
    mutationFn: () => (partner ? api.put(`/api/partners/${partner.id}`, d) : api.post('/api/partners', d)),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['partners'] }); onClose(); },
  });
  const fe = save.error instanceof ApiError ? save.error.fields ?? {} : {};
  const num = (k: 'commissionPct' | 'maxTerm' | 'minDownPct' | 'dtiPct', label: string) => (
    <Field label={label} err={fe[k]}>
      <input className="input mono" type="number" min={0} value={d[k]} onChange={(e) => setD({ ...d, [k]: Number(e.target.value) })} />
    </Field>
  );
  return (
    <Modal title={partner ? partner.name : t('addPartner')} onClose={onClose}
      footer={<><Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn><Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn></>}>
      <div className="col" style={{ gap: 14 }}>
        {save.error && !Object.keys(fe).length && <ErrorBox error={save.error} />}
        <div className="form-grid">
          <Field label={t('partnerCode')} err={fe.code}>
            <input className="input mono" value={d.code} disabled={!!partner} maxLength={20}
              onChange={(e) => setD({ ...d, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '') })} />
          </Field>
          <Field label={t('name')} err={fe.name}>
            <input className="input" value={d.name} onChange={(e) => setD({ ...d, name: e.target.value })} />
          </Field>
          {num('commissionPct', t('commissionPct'))}
          {num('maxTerm', t('maxTerm'))}
          {num('minDownPct', t('minDownPct'))}
          {num('dtiPct', t('dtiPct'))}
        </div>
        <label className="check-row"><input type="checkbox" checked={d.active} onChange={(e) => setD({ ...d, active: e.target.checked })} />{t('active')}</label>
      </div>
    </Modal>
  );
}

/* ------------------------------------------------------------------ xodimlar */

function UsersCard() {
  const { t } = useTranslation();
  const [edit, setEdit] = useState<UserView | 'new' | null>(null);
  const qc = useQueryClient();
  const users = useQuery({ queryKey: ['users'], queryFn: () => api.get<UserView[]>('/api/users') });
  const bulk = useMutation({
    mutationFn: (active: boolean) => api.post<{ changed: number }>('/api/users/bulk-active', { active }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['users'] }); void qc.invalidateQueries({ queryKey: ['users-brief'] }); },
  });
  const list = users.data ?? [];
  return (
    <Card>
      <CardHead title={t('users')}
        sub={`${t('usersSub')} · ${t('active')}: ${list.filter((u) => u.active).length} / ${list.length}`}
        right={
          <div className="row" style={{ gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            <Btn size="sm" variant="ghost" icon="check" disabled={bulk.isPending} onClick={() => bulk.mutate(true)}>{t('usersActivateAll')}</Btn>
            <Btn size="sm" variant="ghost" icon="lock" disabled={bulk.isPending}
              onClick={() => { if (window.confirm(t('usersBlockAllConfirm'))) bulk.mutate(false); }}>{t('usersBlockAll')}</Btn>
            <Btn size="sm" icon="plus" onClick={() => setEdit('new')}>{t('addUser')}</Btn>
          </div>
        } />
      <ErrorBox error={users.error ?? bulk.error} />
      {bulk.data && <div className="card-pad faint" style={{ fontSize: 12, paddingTop: 8, paddingBottom: 0 }}>{t('usersBulkDone', { n: bulk.data.changed })}</div>}
      <div className="table-wrap">
        <table className="tbl">
          <thead><tr><th>{t('fio')}</th><th>{t('role')}</th><th>{t('branch')}</th><th>{t('permissions')}</th><th>{t('status')}</th></tr></thead>
          <tbody>
            {users.data?.map((u) => (
              <tr key={u.id} onClick={() => setEdit(u)} style={{ opacity: u.active ? 1 : 0.55 }}>
                <td className="cell-main">
                  <div className="cell-client">
                    <Avatar name={u.fullName} size={32} />
                    <div className="col" style={{ gap: 1 }}><b>{u.fullName}</b><span className="mono">{u.username}</span></div>
                  </div>
                </td>
                <td data-label={t('role')}>{t(`r_${u.role}`)}</td>
                <td data-label={t('branch')}>{u.branchName ?? <span className="faint">{t('headOffice')}</span>}</td>
                <td data-label={t('permissions')}>
                  {u.customPermissions
                    ? <Badge tone="warn"><Icon name="key" size={12} /> {t('permsCustom')} · {u.permissions.length}</Badge>
                    : <Badge tone="neutral">{t('permsByRole')} · {u.permissions.length}</Badge>}
                </td>
                <td data-label={t('status')}>{u.active ? <Badge tone="success" dot>{t('active')}</Badge> : <Badge tone="neutral">{t('inactive')}</Badge>}</td>
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
  const { user: me, refresh } = useAuth();
  const { branches } = useBranch();
  const matrix = useQuery({ queryKey: ['permissions'], queryFn: () => api.get<PermissionMatrix>('/api/permissions') });
  const [username, setUsername] = useState(user?.username ?? '');
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [role, setRole] = useState<Role>(user?.role ?? 'CREDIT_OFFICER');
  const [branchId, setBranchId] = useState<number | ''>(user?.branchId ?? '');
  const [active, setActive] = useState(user?.active ?? true);
  const [password, setPassword] = useState('');
  const [custom, setCustom] = useState(user?.customPermissions ?? false);
  const [perms, setPerms] = useState<Set<Permission>>(new Set(user?.permissions ?? []));
  const customRoles = useQuery({ queryKey: ['custom-roles'], queryFn: () => api.get<CustomRole[]>('/api/permissions/custom-roles') });
  const [customRoleId, setCustomRoleId] = useState<number | ''>(user?.customRoleId ?? '');
  const [profile, setProfile] = useState({
    pinfl: user?.pinfl ?? '', passportSeries: user?.passportSeries ?? '', birthDate: user?.birthDate ?? '',
    homeAddress: user?.homeAddress ?? '', phone: user?.phone ?? '', email: user?.email ?? '',
    allowNasiya: user?.allowNasiya ?? false, nasiyaNote: user?.nasiyaNote ?? '',
  });
  const cr = customRoles.data?.find((x) => x.id === customRoleId);
  const roleDefaults = cr?.permissions ?? matrix.data?.roles.find((r) => r.role === role)?.permissions ?? [];

  // Shaxsiy ruxsatlar yoqilganda — rol ruxsatlaridan boshlanadi
  const enableCustom = (on: boolean) => {
    setCustom(on);
    if (on && !user?.customPermissions) setPerms(new Set(roleDefaults));
  };
  const save = useMutation({
    mutationFn: () => {
      const body = {
        fullName, role, active, branchId: branchId === '' ? null : branchId,
        permissions: custom && role !== 'ADMIN' ? [...perms] : null,
        customRoleId: role !== 'ADMIN' && customRoleId !== '' ? customRoleId : null,
        profile: { ...profile, birthDate: profile.birthDate || null },
      };
      return user
        ? api.put(`/api/users/${user.id}`, { ...body, newPassword: password || undefined })
        : api.post('/api/users', { ...body, username, password });
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['users'] });
      void qc.invalidateQueries({ queryKey: ['users-brief'] });
      if (user?.id === me?.id) void refresh();
      onClose();
    },
  });
  const isSelf = user?.id === me?.id;
  const remove = useMutation({
    mutationFn: () => api.del(`/api/users/${user!.id}`),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['users'] }); void qc.invalidateQueries({ queryKey: ['users-brief'] }); onClose(); },
  });
  // F.I.Sh dan login: "Aliyev Vali" -> "v.aliyev"
  const genLogin = () => {
    const parts = fullName.trim().toLowerCase().replace(/[ʻʼ'`’‘]/g, '').split(/\s+/).filter(Boolean)
      .map((p) => p.replace(/[^a-z0-9]/g, ''));
    if (parts.length === 0 || !parts[0]) return;
    setUsername((parts[1] ? `${parts[1][0]}.${parts[0]}` : parts[0]).slice(0, 50));
  };
  return (
    <Modal wide title={user ? `${t('edit')} · ${user.fullName}` : t('addUser')} onClose={onClose}
      footer={<>
        {user && !isSelf && (
          <Btn variant="danger" icon="x" disabled={remove.isPending} style={{ marginRight: 'auto' }}
            onClick={() => { if (window.confirm(t('userDeleteConfirm', { name: user.fullName }))) remove.mutate(); }}>{t('userDelete')}</Btn>
        )}
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending || !fullName || (!user && (!username || password.length < 8))} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error ?? remove.error} />
        <div className="form-grid">
          {!user && (
            <Field label={t('username')} req>
              <div className="row" style={{ gap: 8 }}>
                <input className="input" value={username} onChange={(e) => setUsername(e.target.value)} />
                <Btn variant="ghost" icon="spark" disabled={!fullName.trim()} onClick={genLogin}>{t('userGenLogin')}</Btn>
              </div>
            </Field>
          )}
          <Field label={t('fio')} req><input className="input" value={fullName} onChange={(e) => setFullName(e.target.value)} /></Field>
          <Field label={t('role')} hint={isSelf ? t('selfRoleLocked') : undefined}>
            <select className="select" value={role} disabled={isSelf} onChange={(e) => setRole(e.target.value as Role)}>
              {ROLES.map((r) => <option key={r} value={r}>{t(`r_${r}`)}</option>)}
            </select>
          </Field>
          {role !== 'ADMIN' && (
            <Field label={t('customRole')} hint={t('customRoleHint')}>
              <select className="select" value={customRoleId} onChange={(e) => { setCustomRoleId(e.target.value ? Number(e.target.value) : ''); setCustom(false); }}>
                <option value="">—</option>
                {customRoles.data?.map((x) => <option key={x.id} value={x.id}>{x.name}</option>)}
              </select>
            </Field>
          )}
          <Field label={t('branch')} hint={t('branchUserHint')}>
            <select className="select" value={branchId} onChange={(e) => setBranchId(e.target.value ? Number(e.target.value) : '')}>
              <option value="">{t('headOffice')}</option>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.name}{b.active ? '' : ` (${t('inactive')})`}</option>)}
            </select>
          </Field>
          <Field label={user ? t('newPassword') : t('password')} req={!user} hint="min 8">
            <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
          </Field>
        </div>
        {user && !isSelf && <label className="check-row"><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />{t('active')}</label>}

        <div className="divider" />
        <b style={{ fontSize: 13 }}>{t('employeeProfile')}</b>
        <div className="form-grid">
          <Field label={t('pinfl')} hint={t('employeePinflHint')}>
            <input className="input mono" maxLength={14} value={profile.pinfl} onChange={(e) => setProfile({ ...profile, pinfl: e.target.value.replace(/\D/g, '') })} />
          </Field>
          <Field label={t('passport')}><input className="input" value={profile.passportSeries} onChange={(e) => setProfile({ ...profile, passportSeries: e.target.value })} /></Field>
          <Field label={t('birth')}><input className="input" type="date" value={profile.birthDate} onChange={(e) => setProfile({ ...profile, birthDate: e.target.value })} /></Field>
          <Field label={t('phone')}><input className="input" value={profile.phone} onChange={(e) => setProfile({ ...profile, phone: e.target.value })} /></Field>
          <Field label="Email"><input className="input" type="email" value={profile.email} onChange={(e) => setProfile({ ...profile, email: e.target.value })} /></Field>
          <Field label={t('address')}><input className="input" value={profile.homeAddress} onChange={(e) => setProfile({ ...profile, homeAddress: e.target.value })} /></Field>
        </div>
        <label className="check-row"><input type="checkbox" checked={profile.allowNasiya} onChange={(e) => setProfile({ ...profile, allowNasiya: e.target.checked })} />{t('allowNasiya')}</label>
        {!profile.allowNasiya && <span className="faint" style={{ fontSize: 12 }}>{t('allowNasiyaOff')}</span>}
        <Field label={t('nasiyaNote')}><input className="input" maxLength={300} value={profile.nasiyaNote} onChange={(e) => setProfile({ ...profile, nasiyaNote: e.target.value })} /></Field>

        {role !== 'ADMIN' && matrix.data && (
          <>
            <div className="divider" />
            <div className="row between" style={{ flexWrap: 'wrap', gap: 8 }}>
              <div className="col" style={{ gap: 2 }}>
                <b style={{ fontSize: 13 }}>{t('permissions')}</b>
                <span className="faint" style={{ fontSize: 12 }}>{custom ? t('permsCustomHint') : t('permsByRoleHint')}</span>
              </div>
              <div className="seg">
                <button type="button" data-on={!custom} onClick={() => enableCustom(false)}>{t('permsByRole')}</button>
                <button type="button" data-on={custom} onClick={() => enableCustom(true)}>{t('permsCustom')}</button>
              </div>
            </div>
            <PermissionChecklist matrix={matrix.data} value={custom ? perms : new Set(roleDefaults)}
              disabled={!custom} onChange={setPerms} />
          </>
        )}
        {role === 'ADMIN' && <div className="stage-tip st-info"><Icon name="shieldCheck" size={16} /><span>{t('adminAllPerms')}</span></div>}
      </div>
    </Modal>
  );
}

function PermissionChecklist({ matrix, value, disabled, onChange }: {
  matrix: PermissionMatrix; value: Set<Permission>; disabled?: boolean; onChange: (s: Set<Permission>) => void;
}) {
  const { t } = useTranslation();
  const grouped = useMemo(() => SECTIONS.map((s) => ({ s, perms: matrix.permissions.filter((p) => p.section === s) }))
    .filter((g) => g.perms.length), [matrix]);
  const toggle = (p: Permission) => {
    const n = new Set(value);
    if (n.has(p)) n.delete(p);
    else n.add(p);
    onChange(n);
  };
  return (
    <div className="perm-groups" data-disabled={disabled}>
      {grouped.map((g) => (
        <div key={g.s} className="perm-group">
          <span className="perm-sec">{t(`sec_${g.s}`)}</span>
          {g.perms.map((p) => (
            <label key={p.key} className="perm-item" title={t(`ph_${p.key}`)}>
              <input type="checkbox" checked={value.has(p.key)} disabled={disabled} onChange={() => toggle(p.key)} />
              <span>{t(`p_${p.key}`)}</span>
            </label>
          ))}
        </div>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ rollar matritsasi */

function RolesCard() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const { refresh } = useAuth();
  const matrix = useQuery({ queryKey: ['permissions'], queryFn: () => api.get<PermissionMatrix>('/api/permissions') });
  const [draft, setDraft] = useState<Partial<Record<Role, Set<Permission>>>>({});
  const done = (m: PermissionMatrix) => {
    qc.setQueryData(['permissions'], m);
    void qc.invalidateQueries({ queryKey: ['users'] });
    void refresh();
    setDraft({});
  };
  const save = useMutation({ mutationFn: (role: Role) => api.put<PermissionMatrix>(`/api/permissions/roles/${role}`, { permissions: [...draft[role]!] }), onSuccess: done });
  const reset = useMutation({ mutationFn: (role: Role) => api.del<PermissionMatrix>(`/api/permissions/roles/${role}`), onSuccess: done });
  if (!matrix.data) return <ErrorBox error={matrix.error} />;
  const m = matrix.data;
  const current = (r: Role) => draft[r] ?? new Set(m.roles.find((x) => x.role === r)!.permissions);
  const toggle = (r: Role, p: Permission) => {
    const n = new Set(current(r));
    if (n.has(p)) n.delete(p);
    else n.add(p);
    setDraft({ ...draft, [r]: n });
  };
  return (
    <Card>
      <CardHead title={t('rolesPerms')} sub={t('rolesPermsSub')} />
      <ErrorBox error={save.error ?? reset.error} />
      <div className="table-wrap">
        <table className="tbl perm-matrix">
          <thead><tr>
            <th>{t('permission')}</th>
            {m.roles.map((r) => (
              <th key={r.role} className="pm-role">
                <div className="col center" style={{ gap: 4 }}>
                  <span>{t(`r_${r.role}`)}</span>
                  {r.locked ? <Badge tone="neutral"><Icon name="lock" size={11} /></Badge>
                    : draft[r.role] ? <Btn size="sm" disabled={save.isPending} onClick={() => save.mutate(r.role)}>{t('save')}</Btn>
                      : r.customized ? <button className="link-btn" onClick={() => reset.mutate(r.role)}>{t('resetDefaults')}</button>
                        : <span className="faint" style={{ fontSize: 10.5, fontWeight: 500 }}>{t('defaults')}</span>}
                </div>
              </th>
            ))}
          </tr></thead>
          <tbody>
            {SECTIONS.map((s) => {
              const perms = m.permissions.filter((p) => p.section === s);
              if (!perms.length) return null;
              return [
                <tr key={s} className="pm-sec"><td colSpan={m.roles.length + 1}>{t(`sec_${s}`)}</td></tr>,
                ...perms.map((p) => (
                  <tr key={p.key} style={{ cursor: 'default' }}>
                    <td className="cell-main">
                      <div className="col" style={{ gap: 1 }}>
                        <b style={{ fontSize: 13 }}>{t(`p_${p.key}`)}</b>
                        <span className="faint" style={{ fontSize: 11.5 }}>{t(`ph_${p.key}`)}</span>
                      </div>
                    </td>
                    {m.roles.map((r) => (
                      <td key={r.role} className="pm-cell">
                        <input type="checkbox" checked={current(r.role).has(p.key)} disabled={r.locked}
                          aria-label={`${t(`r_${r.role}`)}: ${t(`p_${p.key}`)}`} onChange={() => toggle(r.role, p.key)} />
                      </td>
                    ))}
                  </tr>
                )),
              ];
            })}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

/* ------------------------------------------------------------------ shaxsiy rollar */

function CustomRolesCard() {
  const { t } = useTranslation();
  const [edit, setEdit] = useState<CustomRole | 'new' | null>(null);
  const list = useQuery({ queryKey: ['custom-roles'], queryFn: () => api.get<CustomRole[]>('/api/permissions/custom-roles') });
  return (
    <Card>
      <CardHead title={t('customRoles')} sub={t('customRolesSub')} right={<Btn size="sm" icon="plus" onClick={() => setEdit('new')}>{t('addCustomRole')}</Btn>} />
      <ErrorBox error={list.error} />
      <div className="table-wrap">
        <table className="tbl">
          <thead><tr><th>{t('name')}</th><th>{t('baseRole')}</th><th className="num">{t('permissions')}</th><th className="num">{t('users')}</th></tr></thead>
          <tbody>
            {list.data?.map((r) => (
              <tr key={r.id} onClick={() => setEdit(r)}>
                <td className="cell-main">
                  <div className="row" style={{ gap: 8 }}>
                    <i className="fin-dot" style={{ background: r.color, width: 12, height: 12, borderRadius: 4 }} />
                    <div className="col" style={{ gap: 1 }}><b>{r.name}</b>{r.description && <span className="faint" style={{ fontSize: 11.5 }}>{r.description}</span>}</div>
                  </div>
                </td>
                <td data-label={t('baseRole')}>{t(`r_${r.baseRole}`)}</td>
                <td className="num mono" data-label={t('permissions')}>{r.permissions.length}</td>
                <td className="num mono" data-label={t('users')}>{r.users}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {list.data && list.data.length === 0 && <Empty />}
      {edit && <CustomRoleModal role={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </Card>
  );
}

function CustomRoleModal({ role, onClose }: { role: CustomRole | null; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const { refresh } = useAuth();
  const matrix = useQuery({ queryKey: ['permissions'], queryFn: () => api.get<PermissionMatrix>('/api/permissions') });
  const [name, setName] = useState(role?.name ?? '');
  const [description, setDescription] = useState(role?.description ?? '');
  const [color, setColor] = useState(role?.color ?? '#7c3aed');
  const [baseRole, setBaseRole] = useState<Role>(role?.baseRole ?? 'CREDIT_OFFICER');
  const [perms, setPerms] = useState<Set<Permission>>(new Set(role?.permissions ?? []));
  const done = () => {
    void qc.invalidateQueries({ queryKey: ['custom-roles'] });
    void qc.invalidateQueries({ queryKey: ['users'] });
    void refresh();
    onClose();
  };
  const save = useMutation({
    mutationFn: () => {
      const body = { name, description: description || undefined, color, baseRole, permissions: [...perms] };
      return role ? api.put(`/api/permissions/custom-roles/${role.id}`, body) : api.post('/api/permissions/custom-roles', body);
    },
    onSuccess: done,
  });
  const remove = useMutation({ mutationFn: () => api.del(`/api/permissions/custom-roles/${role!.id}`), onSuccess: done });
  return (
    <Modal wide title={role ? role.name : t('addCustomRole')} onClose={onClose}
      footer={<>
        {role && <Btn variant="ghost" icon="x" disabled={remove.isPending} onClick={() => remove.mutate()}>{t('delete')}</Btn>}
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={!name || save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error ?? remove.error} />
        <div className="form-grid">
          <Field label={t('name')} req><input className="input" value={name} maxLength={80} onChange={(e) => setName(e.target.value)} /></Field>
          <Field label={t('baseRole')} hint={t('baseRoleHint')}>
            <select className="select" value={baseRole} onChange={(e) => setBaseRole(e.target.value as Role)}>
              {ROLES.filter((r) => r !== 'ADMIN').map((r) => <option key={r} value={r}>{t(`r_${r}`)}</option>)}
            </select>
          </Field>
          <Field label={t('description')}><input className="input" value={description} maxLength={200} onChange={(e) => setDescription(e.target.value)} /></Field>
          <Field label={t('color')}>
            <div className="row gap-sm"><input type="color" value={color} onChange={(e) => setColor(e.target.value)} /><input className="input mono" value={color} onChange={(e) => setColor(e.target.value)} /></div>
          </Field>
        </div>
        {matrix.data && <PermissionChecklist matrix={matrix.data} value={perms} onChange={setPerms} />}
      </div>
    </Modal>
  );
}

/* ------------------------------------------------------------------ hujjat shablonlari */

const PLACEHOLDERS = ['company', 'client', 'pinfl', 'passport', 'address', 'phone', 'contract_no', 'contract_date', 'product',
  'sale_price', 'down_payment', 'installment_total', 'monthly', 'term', 'paid', 'remaining', 'overdue', 'days_late',
  'deadline', 'doc_no', 'today', 'branch'];

function DocumentsCard() {
  const { t } = useTranslation();
  const list = useQuery({ queryKey: ['doc-templates'], queryFn: () => api.get<DocTemplate[]>('/api/documents/templates') });
  const [code, setCode] = useState<DocCode>('DEMAND');
  const [lang, setLang] = useState<'uz' | 'ru'>('uz');
  const tpl = list.data?.find((x) => x.code === code && x.lang === lang);
  return (
    <Card>
      <CardHead title={t('docTemplates')} sub={t('docTemplatesSub')} />
      <div className="card-pad col" style={{ gap: 14 }}>
        <div className="row" style={{ gap: 10, flexWrap: 'wrap' }}>
          <div className="seg">
            {(['CONTRACT', 'DEMAND', 'CLAIM'] as DocCode[]).map((c) => (
              <button key={c} type="button" data-on={code === c} onClick={() => setCode(c)}>{t(`doc_${c}`)}</button>
            ))}
          </div>
          <div className="seg">
            {(['uz', 'ru'] as const).map((l) => (
              <button key={l} type="button" data-on={lang === l} onClick={() => setLang(l)} style={{ textTransform: 'uppercase' }}>{l}</button>
            ))}
          </div>
          {tpl && <span className="faint" style={{ fontSize: 11.5, marginLeft: 'auto' }}>{t('updated')}: {fmtDateTime(tpl.updatedAt)}</span>}
        </div>
        <ErrorBox error={list.error} />
        {!tpl && list.data && <Empty />}
        {tpl && <TemplateEditor key={`${tpl.code}-${tpl.lang}-${tpl.updatedAt}`} tpl={tpl} />}
      </div>
    </Card>
  );
}

function TemplateEditor({ tpl }: { tpl: DocTemplate }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [title, setTitle] = useState(tpl.title);
  const [body, setBody] = useState(tpl.body);
  const area = useRef<HTMLTextAreaElement>(null);
  const save = useMutation({
    mutationFn: () => api.put<DocTemplate>(`/api/documents/templates/${tpl.code}/${tpl.lang}`, { title, body }),
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['doc-templates'] }),
  });
  const insert = (ph: string) => {
    const el = area.current;
    const token = `{${ph}}`;
    if (!el) return setBody(body + token);
    const { selectionStart: a, selectionEnd: b } = el;
    setBody(body.slice(0, a) + token + body.slice(b));
    requestAnimationFrame(() => {
      el.focus();
      el.setSelectionRange(a + token.length, a + token.length);
    });
  };
  const dirty = title !== tpl.title || body !== tpl.body;
  return (
    <>
      <Field label={t('docTitle')}><input className="input" value={title} onChange={(e) => setTitle(e.target.value)} /></Field>
      <Field label={t('docBody')} hint={t('docBodyHint')}>
        <textarea ref={area} className="input doc-editor" rows={16} value={body} onChange={(e) => setBody(e.target.value)} />
      </Field>
      <div className="col" style={{ gap: 6 }}>
        <span className="faint" style={{ fontSize: 12 }}>{t('placeholders')}</span>
        <div className="ph-chips">
          {PLACEHOLDERS.map((p) => <button key={p} type="button" className="ph-chip mono" onClick={() => insert(p)}>{`{${p}}`}</button>)}
        </div>
      </div>
      <div className="stage-tip st-warn"><Icon name="alert" size={16} /><span>{t('docNoPenaltyWarn')}</span></div>
      <ErrorBox error={save.error} />
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <Btn size="sm" icon="check" disabled={!dirty || save.isPending} onClick={() => save.mutate()}>{save.isSuccess && !dirty ? t('saved') : t('save')}</Btn>
      </div>
    </>
  );
}
