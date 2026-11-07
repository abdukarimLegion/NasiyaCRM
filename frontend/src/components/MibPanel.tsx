import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { MibCheck, MibState } from '../api/types';
import { useAuth } from '../lib/auth';
import { fmtDateTime, fmtSom } from '../lib/format';
import { Badge, Btn, Card, CardHead, ErrorBox, Field, Icon, Modal } from './ui';

const TONE = { CLEAR: 'success', DEBT: 'danger', ERROR: 'warn' } as const;

/**
 * MIB (Majburiy ijro byurosi) bo'yicha qarzdorlik: JShShIR orqali tekshiriladi, natija mijozda saqlanadi.
 * auto — natija eskirgan bo'lsa panel ochilganda o'zi tekshiradi (nasiya wizard'ida).
 */
export default function MibPanel({ clientId, auto = false }: { clientId: number; auto?: boolean }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const qc = useQueryClient();
  const key = ['mib', clientId];
  const [manual, setManual] = useState(false);
  const [open, setOpen] = useState(false);
  const state = useQuery({ queryKey: key, queryFn: () => api.get<MibState>(`/api/mib/clients/${clientId}`) });
  const check = useMutation({
    mutationFn: () => api.post<MibCheck>(`/api/mib/clients/${clientId}/check`),
    onSettled: () => void qc.invalidateQueries({ queryKey: key }),
  });

  // Wizard'da: rejim yoqilgan, xizmat sozlangan va amaldagi natija yo'q bo'lsa — bir marta avtomatik tekshiradi
  const asked = useRef<number | null>(null);
  const s = state.data;
  useEffect(() => {
    if (auto && s && s.needsCheck && s.providerConfigured && asked.current !== clientId && can('CONTRACTS_CREATE')) {
      asked.current = clientId;
      check.mutate();
    }
  }, [auto, s, clientId, can, check]);

  if (!s) return <ErrorBox error={state.error} />;
  // Rejim o'chiq va hech qachon tekshirilmagan bo'lsa ham qo'lda tekshirish tugmasi ko'rinadi (ixtiyoriy)
  const shown = s.decisive ?? s.latest;
  const debt = s.decisive?.status === 'DEBT';
  return (
    <Card>
      <CardHead title={<><Icon name="scale" size={16} /> {t('mibTitle')}</>} sub={t('mibSub')}
        right={
          <div className="row" style={{ gap: 6 }}>
            {can('CONTRACTS_APPROVE') && <Btn size="sm" variant="ghost" onClick={() => setManual(true)}>{t('mibManual')}</Btn>}
            {can('CONTRACTS_CREATE') && (
              <Btn size="sm" variant="soft" icon="refresh" disabled={check.isPending || !s.providerConfigured} onClick={() => check.mutate()}>
                {check.isPending ? t('mibChecking') : t('mibCheck')}
              </Btn>
            )}
          </div>
        } />
      <div className="card-pad col" style={{ gap: 12 }}>
        <ErrorBox error={check.error} />
        {!s.providerConfigured && (
          <div className="stage-tip st-warn" style={{ marginTop: 0 }}><Icon name="alert" size={18} /><span>{t('mibNotConfigured')}</span></div>
        )}
        {shown ? (
          <div className="col" style={{ gap: 8 }}>
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <Badge tone={TONE[shown.status]} dot>{t(`mib_${shown.status}`)}</Badge>
              {debt && <b className="mono" style={{ color: 'var(--danger)' }}>{fmtSom(s.decisive!.totalDebt, lang)}</b>}
              <span className="faint" style={{ fontSize: 12 }}>
                {fmtDateTime(shown.createdAt, lang)} · {t(`mibSrc_${shown.source}`)}{shown.checkedBy ? ` · ${shown.checkedBy}` : ''}
              </span>
              {s.decisive && !s.fresh && <Badge tone="warn">{t('mibStale', { n: s.validityDays })}</Badge>}
            </div>
            {debt && (
              <div className="stage-tip st-danger" style={{ marginTop: 0 }}><Icon name="alert" size={18} /><span>{t('mibDebtWarn')}</span></div>
            )}
            {debt && s.decisive!.cases.length > 0 && (
              <ul className="mib-cases">
                {s.decisive!.cases.map((c, i) => (
                  <li key={i} className="mono">{Object.entries(c).map(([k, v]) => `${k}: ${v}`).join(' · ')}</li>
                ))}
              </ul>
            )}
            {shown.message && <span className="faint" style={{ fontSize: 12 }}>{shown.message}</span>}
          </div>
        ) : <span className="faint">{t('mibNone')}</span>}
        {s.mode !== 'OFF' && <span className="faint" style={{ fontSize: 11.5 }}>{t(`mibMode_${s.mode}`, { n: s.validityDays })}</span>}
        {s.history.length > 1 && (
          <>
            <button className="link-btn" style={{ alignSelf: 'flex-start' }} onClick={() => setOpen(!open)}>{t('mibHistory')} ({s.history.length})</button>
            {open && s.history.map((h) => (
              <div key={h.id} className="row between" style={{ fontSize: 12 }}>
                <span><Badge tone={TONE[h.status]}>{t(`mib_${h.status}`)}</Badge> {t(`mibSrc_${h.source}`)}</span>
                <span className="faint">{fmtDateTime(h.createdAt, lang)}</span>
              </div>
            ))}
          </>
        )}
      </div>
      {manual && <ManualModal clientId={clientId} onClose={() => setManual(false)} />}
    </Card>
  );
}

function ManualModal({ clientId, onClose }: { clientId: number; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [hasDebt, setHasDebt] = useState(false);
  const [totalDebt, setTotalDebt] = useState(0);
  const [caseCount, setCaseCount] = useState(1);
  const [note, setNote] = useState('');
  const save = useMutation({
    mutationFn: () => api.post(`/api/mib/clients/${clientId}/manual`, { hasDebt, totalDebt: hasDebt ? totalDebt : 0, caseCount: hasDebt ? caseCount : 0, note: note || undefined }),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['mib', clientId] }); onClose(); },
  });
  return (
    <Modal title={t('mibManual')} onClose={onClose}
      footer={<><Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn><Btn icon="check" disabled={save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn></>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <span className="faint" style={{ fontSize: 12 }}>{t('mibManualHint')}</span>
        <div className="seg">
          <button type="button" data-on={!hasDebt} onClick={() => setHasDebt(false)}>{t('mib_CLEAR')}</button>
          <button type="button" data-on={hasDebt} onClick={() => setHasDebt(true)}>{t('mib_DEBT')}</button>
        </div>
        {hasDebt && (
          <div className="form-grid">
            <Field label={t('mibTotalDebt')}><input className="input mono" type="number" min={0} value={totalDebt} onChange={(e) => setTotalDebt(Number(e.target.value))} /></Field>
            <Field label={t('mibCases')}><input className="input mono" type="number" min={1} value={caseCount} onChange={(e) => setCaseCount(Number(e.target.value))} /></Field>
          </div>
        )}
        <Field label={t('note')} hint={t('mibNoteHint')}><input className="input" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
      </div>
    </Modal>
  );
}
