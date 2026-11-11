import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { PnlReport } from '../api/types';
import { downloadCsv } from '../lib/csv';
import { fmtDate, fmtSom, todayIso } from '../lib/format';
import { Btn, Card, CardHead, ErrorBox, Field } from './ui';

type Preset = 'today' | 'month' | 'prevMonth' | 'quarter' | 'ytd' | 'custom';
const PRESETS: Preset[] = ['today', 'month', 'prevMonth', 'quarter', 'ytd', 'custom'];

const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

function range(p: Preset, custom: { from: string; to: string }): { from: string; to: string } {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  switch (p) {
    case 'today': return { from: iso(now), to: iso(now) };
    case 'month': return { from: iso(new Date(y, m, 1)), to: iso(now) };
    case 'prevMonth': return { from: iso(new Date(y, m - 1, 1)), to: iso(new Date(y, m, 0)) };
    case 'quarter': return { from: iso(new Date(y, m - 2, 1)), to: iso(now) };
    case 'ytd': return { from: iso(new Date(y, 0, 1)), to: iso(now) };
    default: return custom;
  }
}

/** Foyda va zarar hisoboti (P&L): davr tanlanadi, moddalar jadval ko'rinishida, CSV'ga yuklab olinadi. */
export default function PnlStatement({ branchId }: { branchId?: number | null }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const [preset, setPreset] = useState<Preset>('month');
  const [custom, setCustom] = useState({ from: todayIso().slice(0, 8) + '01', to: todayIso() });
  const r = range(preset, custom);
  const valid = !!r.from && !!r.to && r.from <= r.to;
  const q = useQuery({
    queryKey: ['pnl', r.from, r.to, branchId ?? null],
    queryFn: () => api.get<PnlReport>(`/api/analytics/pnl${qs({ from: r.from, to: r.to, branchId })}`),
    enabled: valid,
    placeholderData: (prev) => prev,
  });
  const d = q.data;

  type Line = { label: string; hint?: string; value: number; kind?: 'head' | 'total' | 'minus' | 'result' };
  const lines: Line[] = d ? [
    { label: t('pnlRevenue'), value: d.grossReceipts, kind: 'head' },
    { label: t('pnlPrincipal'), hint: t('pnlPrincipalHint'), value: d.principalCollected },
    { label: t('pnlMarkup'), hint: t('pnlMarkupHint'), value: d.markupCollected },
    { label: t('pnlOther'), hint: t('pnlOtherHint'), value: d.otherReceipts },
    { label: t('pnlFinancing'), value: -d.financingCosts, kind: 'head' },
    { label: t('pnlFunding', { pct: d.fundingCostPct }), hint: t('pnlFundingHint'), value: -d.fundingCost, kind: 'minus' },
    { label: t('pnlAcquiring', { pct: d.acquiringPct }), hint: t('pnlAcquiringHint'), value: -d.acquiringFee, kind: 'minus' },
    { label: t('pnlPartnerCommission'), hint: t('pnlPartnerCommissionHint'), value: -d.partnerCommission, kind: 'minus' },
    { label: t('pnlGrossMargin'), hint: t('pnlGrossMarginHint'), value: d.grossMargin, kind: 'total' },
    { label: t('pnlOpex'), hint: t('pnlOpexHint'), value: -d.opex, kind: 'minus' },
    { label: t('pnlNet'), hint: `${t('pnlMarginPct')}: ${d.marginPct}%`, value: d.netProfit, kind: 'result' },
    { label: t('pnlEcl'), hint: t('pnlEclHint'), value: -d.eclReserve, kind: 'minus' },
    { label: t('pnlAfterProvision'), value: d.afterProvision, kind: 'total' },
  ] : [];

  const exportCsv = () => {
    if (!d) return;
    downloadCsv(`pnl_${d.from}_${d.to}.csv`, [t('pnlLine'), t('amount')], [
      ...lines.map((l) => [l.label, l.value]),
      [t('pnlIssued'), d.issuedVolume], [t('pnlIssuedContracts'), d.issuedContracts], [t('pnlPartnerVolume'), d.partnerVolume],
    ]);
  };

  return (
    <Card className="block">
      <CardHead title={t('pnlTitle')}
        sub={d ? `${fmtDate(d.from, lang)} — ${fmtDate(d.to, lang)} · ${d.days} ${t('daysShort')}` : t('pnlSub')}
        right={<Btn size="sm" variant="soft" icon="download" disabled={!d} onClick={exportCsv}>{t('pnlCsv')}</Btn>} />
      <div className="card-pad col" style={{ gap: 12, paddingBottom: 6 }}>
        <div className="seg" style={{ flexWrap: 'wrap' }}>
          {PRESETS.map((p) => <button key={p} data-on={preset === p} onClick={() => setPreset(p)}>{t(`pnlP_${p}`)}</button>)}
        </div>
        {preset === 'custom' && (
          <div className="form-grid" style={{ maxWidth: 420 }}>
            <Field label={t('from')}><input className="input" type="date" value={custom.from} max={custom.to} onChange={(e) => setCustom({ ...custom, from: e.target.value })} /></Field>
            <Field label={t('to')}><input className="input" type="date" value={custom.to} min={custom.from} onChange={(e) => setCustom({ ...custom, to: e.target.value })} /></Field>
          </div>
        )}
        <ErrorBox error={q.error} />
      </div>
      {d && (
        <>
          <div className="table-wrap">
            <table className="tbl pnl-tbl">
              <thead><tr><th>{t('pnlLine')}</th><th className="num">{t('amount')}</th></tr></thead>
              <tbody>
                {lines.map((l) => (
                  <tr key={l.label} data-kind={l.kind ?? 'line'} style={{ cursor: 'default' }}>
                    <td>
                      <div className="col" style={{ gap: 1 }}>
                        <span>{l.label}</span>
                        {l.hint && <span className="faint" style={{ fontSize: 11.5, whiteSpace: 'normal' }}>{l.hint}</span>}
                      </div>
                    </td>
                    <td className="num mono" style={{ color: l.value < 0 ? 'var(--danger)' : l.kind === 'result' ? 'var(--success)' : undefined }}>
                      {fmtSom(l.value, lang)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="card-pad faint" style={{ fontSize: 12 }}>
            {t('pnlIssued')}: <b>{fmtSom(d.issuedVolume, lang)}</b> ({d.issuedContracts}) · {t('pnlPartnerVolume')}: <b>{fmtSom(d.partnerVolume, lang)}</b>
            <br />{t('pnlNote')}
          </div>
        </>
      )}
    </Card>
  );
}
