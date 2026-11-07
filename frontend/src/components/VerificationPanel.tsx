import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { OtpSent, VerificationResult, VerificationStatus } from '../api/types';
import { Badge, Btn, Card, CardHead, ErrorBox, Field, Icon } from './ui';

/**
 * Mijozni tasdiqlash (SMS OTP / biometrik natija). Rejim sozlamalardan keladi;
 * "NONE" bo'lsa panel ko'rsatilmaydi. Tasdiqlash 30 daqiqa amal qiladi.
 */
export default function VerificationPanel({ clientId }: { clientId: number }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const key = ['verification', clientId];
  const status = useQuery({
    queryKey: key,
    queryFn: () => api.get<VerificationStatus>(`/api/verifications/clients/${clientId}/status`),
  });
  const [sent, setSent] = useState<OtpSent | null>(null);
  const [code, setCode] = useState('');
  const [match, setMatch] = useState(92);
  const [liveness, setLiveness] = useState(true);
  const [ref, setRef] = useState('');
  const refresh = () => void qc.invalidateQueries({ queryKey: key });

  const send = useMutation({
    mutationFn: () => api.post<OtpSent>(`/api/verifications/clients/${clientId}/otp`),
    onSuccess: (r) => { setSent(r); setCode(''); },
  });
  const confirm = useMutation({
    mutationFn: () => api.post<VerificationResult>('/api/verifications/otp/confirm', { id: sent!.id, code }),
    onSuccess: () => { setSent(null); setCode(''); refresh(); },
  });
  const bio = useMutation({
    mutationFn: () => api.post<VerificationResult>('/api/verifications/biometric',
      { clientId, matchPct: match, livenessPassed: liveness, reference: ref || undefined }),
    onSuccess: refresh,
  });

  const s = status.data;
  if (!s || s.mode === 'NONE') return null;
  const needOtp = s.mode === 'OTP' || s.mode === 'ANY' || s.mode === 'BOTH';
  const needBio = s.mode === 'BIOMETRIC' || s.mode === 'ANY' || s.mode === 'BOTH';
  const otpDone = s.verified.includes('OTP');
  const bioDone = s.verified.includes('BIOMETRIC');

  return (
    <Card>
      <CardHead title={<><Icon name="shieldCheck" size={16} /> {t('verifyTitle')}</>}
        sub={t(`vm_${s.mode}`)}
        right={s.satisfied ? <Badge tone="success" dot>{t('verified')}</Badge> : <Badge tone="warn" dot>{t('verifyRequired')}</Badge>} />
      <div className="card-pad col" style={{ gap: 18 }}>
        {needOtp && (
          <div className="col" style={{ gap: 10 }}>
            <div className="row between">
              <b style={{ fontSize: 13 }}>{t('verifyOtp')}</b>
              {otpDone && <Badge tone="success"><Icon name="check" size={13} /></Badge>}
            </div>
            {!otpDone && (
              <>
                <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
                  <Btn size="sm" variant="soft" icon="message" disabled={send.isPending} onClick={() => send.mutate()}>
                    {sent ? t('verifyResend') : t('verifySend')}
                  </Btn>
                  {sent && <span className="faint" style={{ fontSize: 12 }}>{t('verifySentTo', { phone: sent.maskedPhone })}</span>}
                </div>
                {sent && (
                  <div className="row" style={{ gap: 8, flexWrap: 'wrap', alignItems: 'flex-end' }}>
                    <Field label={t('verifyCode')}>
                      <input className="input mono" style={{ width: 150, letterSpacing: 4 }} inputMode="numeric" maxLength={8}
                        value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))} />
                    </Field>
                    <Btn icon="check" disabled={confirm.isPending || code.length < 4} onClick={() => confirm.mutate()}>{t('verifyConfirm')}</Btn>
                    {sent.devCode && <span className="faint mono" style={{ fontSize: 11.5 }}>dev: {sent.devCode}</span>}
                  </div>
                )}
              </>
            )}
            <ErrorBox error={send.error ?? confirm.error} />
          </div>
        )}

        {needBio && (
          <div className="col" style={{ gap: 10 }}>
            <div className="row between">
              <b style={{ fontSize: 13 }}>{t('verifyBio')}</b>
              {bioDone && <Badge tone="success"><Icon name="check" size={13} /></Badge>}
            </div>
            {!bioDone && (
              <>
                <span className="faint" style={{ fontSize: 12 }}>{t('verifyBioHint', { min: s.minFaceMatchPct })}</span>
                <div className="form-grid">
                  <Field label={`${t('verifyMatch')} · ${match}%`}>
                    <input type="range" className="rng" min={0} max={100} value={match} onChange={(e) => setMatch(Number(e.target.value))} />
                  </Field>
                  <Field label={t('verifyRef')}>
                    <input className="input" value={ref} onChange={(e) => setRef(e.target.value)} maxLength={100} />
                  </Field>
                </div>
                <label className="check-row"><input type="checkbox" checked={liveness} onChange={(e) => setLiveness(e.target.checked)} />{t('verifyLiveness')}</label>
                <div><Btn size="sm" variant="soft" icon="check" disabled={bio.isPending} onClick={() => bio.mutate()}>{t('verifyRecord')}</Btn></div>
                {bio.data?.status === 'FAILED' && <div className="stage-tip st-danger" style={{ marginTop: 0 }}><Icon name="alert" size={18} /><span>{t('verifyBioFailed', { min: s.minFaceMatchPct })}</span></div>}
              </>
            )}
            <ErrorBox error={bio.error} />
          </div>
        )}
      </div>
    </Card>
  );
}
