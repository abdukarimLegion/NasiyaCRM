import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { ExternalLimitReport, ExternalLimitStatus } from '../api/types';
import { fmtDateTime, fmtSom } from '../lib/format';
import { Badge, Btn, Card, CardHead, ErrorBox, Icon } from './ui';

const TONE: Record<ExternalLimitStatus, 'success' | 'warn' | 'neutral' | 'danger'> = {
  AVAILABLE: 'success', NO_LIMIT: 'warn', NOT_REGISTERED: 'neutral', ERROR: 'danger',
};

/**
 * Mijozning tashqi nasiya platformalaridagi (Uzum, Anor, Alif, Payme ...) limiti — JShShIR bo'yicha.
 * Faqat ma'lumot: natija saqlanmaydi, skoring va ichki limitga ta'sir qilmaydi. Hozircha backend
 * namoyish (mock) ma'lumot qaytaradi — bu "DEMO" belgisi bilan ko'rsatiladi.
 */
export default function ExternalLimitsPanel({ clientId }: { clientId: number }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const check = useMutation({
    mutationFn: () => api.post<ExternalLimitReport>(`/api/external-limits/clients/${clientId}/check`),
  });
  const r = check.data;
  return (
    <Card>
      <CardHead title={<><Icon name="wallet" size={16} /> {t('extLimTitle')} {r?.mock && <Badge tone="warn">{t('extLimMock')}</Badge>}</>}
        sub={t('extLimSub')}
        right={
          <Btn size="sm" variant="soft" icon="refresh" disabled={check.isPending} onClick={() => check.mutate()}>
            {check.isPending ? t('extLimChecking') : t('extLimCheck')}
          </Btn>
        } />
      <ErrorBox error={check.error} />
      {!r ? (
        <div className="card-pad"><span className="faint">{t('extLimNone')}</span></div>
      ) : (
        <>
          <div className="table-wrap">
            <table className="tbl">
              <thead>
                <tr>
                  <th>{t('extLimPlatform')}</th>
                  <th>{t('extLimStatus')}</th>
                  <th style={{ textAlign: 'right' }}>{t('extLimTotal')}</th>
                  <th style={{ textAlign: 'right' }}>{t('extLimUsed')}</th>
                  <th style={{ textAlign: 'right' }}>{t('extLimAvailable')}</th>
                  <th>{t('extLimTerm')}</th>
                </tr>
              </thead>
              <tbody>
                {r.items.map((l) => {
                  const has = l.status === 'AVAILABLE';
                  return (
                    <tr key={l.provider}>
                      <td><b>{l.providerName}</b></td>
                      <td><Badge tone={TONE[l.status]} dot>{t(`extLim_${l.status}`)}</Badge></td>
                      <td className="mono" style={{ textAlign: 'right' }}>{has ? fmtSom(l.total, lang) : '—'}</td>
                      <td className="mono" style={{ textAlign: 'right' }}>{has ? fmtSom(l.used, lang) : '—'}</td>
                      <td className="mono" style={{ textAlign: 'right' }}>{has ? <b>{fmtSom(l.available, lang)}</b> : '—'}</td>
                      <td>{has && l.maxTermMonths ? t('extLimTermMonths', { n: l.maxTermMonths }) : '—'}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <div className="card-pad col" style={{ gap: 6 }}>
            <div className="row between" style={{ flexWrap: 'wrap', gap: 8 }}>
              <span>{t('extLimSum')}: <b className="mono">{fmtSom(r.totalAvailable, lang)}</b></span>
              <span className="faint" style={{ fontSize: 12 }}>{fmtDateTime(r.checkedAt, lang)}</span>
            </div>
            {r.mock && <span className="faint" style={{ fontSize: 12 }}>{t('extLimMockHint')}</span>}
          </div>
        </>
      )}
    </Card>
  );
}
