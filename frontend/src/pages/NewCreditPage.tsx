import { Fragment, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type {
  ApplicationView, CartItem, ClientListItem, CreditLimitResult, Factor, Page, Product, Quote, QuoteRequest, ScoringRequest, ScoringResult, StopFactor,
  PartnerOffers, VerificationStatus,
} from '../api/types';
import { Avatar, Badge, Btn, Card, CardHead, ErrorBox, Field, Gauge, Icon } from '../components/ui';
import VerificationPanel from '../components/VerificationPanel';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { fmtDate, fmtSom } from '../lib/format';

const STEPS = ['step_client', 'step_scoring', 'step_stop', 'step_finance', 'step_contract'];
const FACTORS: { key: Factor; max: number }[] = [
  { key: 'INCOME', max: 30 }, { key: 'TENURE', max: 15 }, { key: 'FAMILY', max: 10 },
  { key: 'HISTORY', max: 20 }, { key: 'DISCIPLINE', max: 15 }, { key: 'GUARANTOR', max: 10 },
];
const MANUAL_STOPS: StopFactor[] = ['BLACKLIST', 'FRAUD', 'COURT'];

export default function NewCreditPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const { can } = useAuth();
  const branch = useBranch();
  const [params] = useSearchParams();
  const [step, setStep] = useState(0);

  // 1. mijoz
  const [clientId, setClientId] = useState<number | null>(params.get('clientId') ? Number(params.get('clientId')) : null);
  const [query, setQuery] = useState('');
  const clients = useQuery({
    queryKey: ['clients', query, 0],
    queryFn: () => api.get<Page<ClientListItem>>(`/api/clients${qs({ q: query, size: 30 })}`),
  });

  // mijoz tasdiqlanganmi (sozlamadagi rejimga ko'ra); rejim NONE bo'lsa to'siq yo'q
  const verification = useQuery({
    queryKey: ['verification', clientId],
    queryFn: () => api.get<VerificationStatus>(`/api/verifications/clients/${clientId}/status`),
    enabled: clientId != null,
  });

  // 2-3. scoring
  const [points, setPoints] = useState<Record<Factor, number>>(
    { INCOME: 20, TENURE: 10, FAMILY: 7, HISTORY: 12, DISCIPLINE: 10, GUARANTOR: 5 });
  const [incomeVerified, setIncomeVerified] = useState(true);
  const [guarantorPresent, setGuarantorPresent] = useState(false);
  const [manualStops, setManualStops] = useState<StopFactor[]>([]);
  const scoringReq: ScoringRequest = { points, incomeVerified, guarantorPresent, manualStopFactors: manualStops };
  const scoring = useQuery({
    queryKey: ['scoring', clientId, scoringReq],
    queryFn: () => api.post<ScoringResult>('/api/scoring/evaluate', { clientId, scoring: scoringReq }),
    enabled: clientId != null && step >= 1,
  });

  // ichki limit: daromad × DTI(risk) − ochiq shartnomalardagi oylik to'lovlar
  const limit = useQuery({
    queryKey: ['client-limit', clientId, scoring.data?.risk],
    queryFn: () => api.get<CreditLimitResult>(`/api/clients/${clientId}/limit?risk=${scoring.data!.risk}`),
    enabled: clientId != null && !!scoring.data,
  });

  // 4. murobaha
  const products = useQuery({ queryKey: ['products', 'active'], queryFn: () => api.get<Product[]>('/api/products?activeOnly=true') });
  const [cart, setCart] = useState<CartItem[]>([]);
  const [addId, setAddId] = useState<number | null>(null);
  const [termMonths, setTermMonths] = useState(12);
  const [markupPct, setMarkupPct] = useState<number | undefined>();
  const [downPayment, setDownPayment] = useState(0);
  const [guarantorName, setGuarantorName] = useState('');
  const [guarantorPhone, setGuarantorPhone] = useState('');

  // savat qatorlari: mahsulot + miqdor + summa
  const lines = cart.flatMap((it) => {
    const p = products.data?.find((x) => x.id === it.productId);
    return p ? [{ p, qty: it.quantity, total: p.price * it.quantity }] : [];
  });
  const cartTotal = lines.reduce((a, l) => a + l.total, 0);
  const termMin = lines.length ? Math.max(...lines.map((l) => l.p.termMin)) : 1;
  const termMax = lines.length ? Math.min(...lines.map((l) => l.p.termMax)) : 60;
  const defaultMarkup = cartTotal > 0
    ? Math.round((lines.reduce((a, l) => a + l.p.markupPct * l.total, 0) / cartTotal) * 100) / 100 : 0;
  const termConflict = lines.length > 0 && termMin > termMax;

  const updateCart = (next: CartItem[]) => {
    setCart(next);
    setMarkupPct(undefined);
    const ls = next.flatMap((it) => products.data?.filter((x) => x.id === it.productId) ?? []);
    if (ls.length) {
      const mn = Math.max(...ls.map((x) => x.termMin));
      const mx = Math.min(...ls.map((x) => x.termMax));
      if (mn <= mx) setTermMonths((m) => Math.min(mx, Math.max(mn, m)));
    }
  };
  const addToCart = () => {
    if (addId == null) return;
    const exist = cart.find((c) => c.productId === addId);
    updateCart(exist
      ? cart.map((c) => (c.productId === addId ? { ...c, quantity: c.quantity + 1 } : c))
      : [...cart, { productId: addId, quantity: 1 }]);
    setAddId(null);
  };
  const setQty = (id: number, q: number) => {
    const stock = products.data?.find((x) => x.id === id)?.stock ?? 1;
    updateCart(cart.map((c) => (c.productId === id ? { ...c, quantity: Math.max(1, Math.min(stock || 1, q || 1)) } : c)));
  };

  const quoteReq: QuoteRequest | null = cart.length && !termConflict
    ? { items: cart, termMonths, markupPct, downPayment: downPayment || 0 } : null;
  const quote = useQuery({
    queryKey: ['quote', quoteReq],
    queryFn: () => api.post<Quote>('/api/contracts/quote', quoteReq),
    enabled: !!quoteReq,
  });

  // hamkor provayderlar (Alif / Uzum / TBC): mijoz limiti va tavsiya
  const [route, setRoute] = useState<string | null>(null); // null — tavsiyaga ko'ra, 'INTERNAL' yoki hamkor kodi
  const [partnerTx, setPartnerTx] = useState('');
  const offers = useQuery({
    queryKey: ['partner-offers', clientId, scoring.data?.risk, cartTotal, downPayment, termMonths, quote.data?.installmentTotal],
    queryFn: () => api.get<PartnerOffers>(`/api/partners/offers${qs({
      clientId, risk: scoring.data?.risk, goodsTotal: cartTotal, downPayment: downPayment || 0, term: termMonths,
      internalAmount: quote.data?.installmentTotal,
    })}`),
    enabled: clientId != null && !!scoring.data && !!quote.data,
  });
  const chosen = route ?? offers.data?.recommendation.provider ?? 'INTERNAL';
  const viaPartner = chosen !== 'INTERNAL';

  const create = useMutation({
    mutationFn: async () => {
      const body = {
        clientId, terms: quoteReq, scoring: scoringReq,
        guarantorName: guarantorName || undefined, guarantorPhone: guarantorPhone || undefined,
        branchId: branch.param,
      };
      if (!viaPartner) return api.post<ApplicationView>('/api/applications?contractNow=true', body);
      const a = await api.post<ApplicationView>('/api/applications', body);
      return a.status === 'APPROVED'
        ? api.post<ApplicationView>(`/api/applications/${a.id}/handover`, { partnerCode: chosen, transactionId: partnerTx || undefined })
        : a;
    },
    onSuccess: (a) => nav(a.status === 'CONTRACTED' && a.contractId ? `/contracts/${a.contractId}` : `/applications?focus=${a.id}`),
  });

  const client = useMemo(() => clients.data?.content.find((c) => c.id === clientId), [clients.data, clientId]);
  const decision = scoring.data?.decision;
  // Rad etilgan yoki tasdiqlash kutadigan ariza ham saqlanadi (KPI va tahlil uchun), shuning uchun to'siq yo'q
  const canNext = [
    clientId != null && (verification.data?.satisfied ?? false),
    !!scoring.data,
    !!scoring.data,
    !!quote.data,
    true,
  ][step];
  const finishKey = decision === 'REJECTED' ? 'finishReject'
    : viaPartner && decision === 'APPROVED' ? 'finishPartner'
    : decision === 'REVIEW' && !can('CONTRACTS_APPROVE') ? 'finishReview' : 'finish';

  return (
    <div className="page anim" style={{ maxWidth: 1080 }}>
      <div className="card card-pad" style={{ marginBottom: 22 }}>
        <div className="wiz-steps">
          {STEPS.map((s, i) => (
            <Fragment key={s}>
              <button className="wiz-step" data-state={i === step ? 'active' : i < step ? 'done' : 'todo'}
                onClick={() => i < step && setStep(i)}>
                <span className="wiz-dot">{i < step ? <Icon name="check" size={15} /> : i + 1}</span>
                <span className="wiz-lbl">{t(s)}</span>
              </button>
              {i < STEPS.length - 1 && <span className="wiz-line" data-done={i < step} />}
            </Fragment>
          ))}
        </div>
      </div>

      {step === 0 && (
        <Card>
          <CardHead title={t('selectClient')} />
          <div className="card-pad" style={{ paddingTop: 14, paddingBottom: 14 }}>
            <div className="search" style={{ minWidth: 0, width: '100%' }}>
              <Icon name="search" /><input placeholder={t('search')} value={query} onChange={(e) => setQuery(e.target.value)} />
            </div>
          </div>
          <div className="scroll" style={{ maxHeight: 420, overflowY: 'auto', padding: '0 12px 12px' }}>
            {clients.data?.content.map((c) => (
              <button key={c.id} className="client-pick" data-on={c.id === clientId} onClick={() => setClientId(c.id)}>
                <Avatar name={c.fullName} size={38} />
                <div className="col" style={{ gap: 1, minWidth: 0, flex: 1, textAlign: 'left' }}>
                  <b style={{ fontSize: 14 }}>{c.fullName}</b>
                  <span className="faint mono" style={{ fontSize: 11.5 }}>{c.pinfl} · {c.phone}</span>
                </div>
                {c.id === clientId && <Icon name="check" />}
              </button>
            ))}
          </div>
        </Card>
      )}
      {step === 0 && clientId != null && <div style={{ marginTop: 16 }}><VerificationPanel clientId={clientId} /></div>}

      {step === 1 && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('step_scoring')} sub={client?.fullName} />
            <div className="card-pad col" style={{ gap: 16 }}>
              {FACTORS.map((f) => (
                <Field key={f.key} label={`${t(`f_${f.key}`)} · ${points[f.key]} / ${f.max}`}>
                  <input type="range" className="rng" min={0} max={f.max} value={points[f.key]}
                    onChange={(e) => setPoints((p) => ({ ...p, [f.key]: Number(e.target.value) }))} />
                </Field>
              ))}
              <label className="check-row"><input type="checkbox" checked={incomeVerified}
                onChange={(e) => setIncomeVerified(e.target.checked)} />{t('incomeVerified')}</label>
              <label className="check-row"><input type="checkbox" checked={guarantorPresent}
                onChange={(e) => setGuarantorPresent(e.target.checked)} />{t('guarantorPresent')}</label>
            </div>
          </Card>
          <Card>
            <div className="card-pad col center" style={{ gap: 12 }}>
              {scoring.data && <Gauge value={scoring.data.total} cat={scoring.data.risk}
                label={t('scoreOf')} sublabel={`${t('riskCategory')} ${scoring.data.risk}`} />}
              <ErrorBox error={scoring.error} />
              {limit.data && <LimitCard l={limit.data} />}
            </div>
          </Card>
        </div>
      )}

      {step === 2 && scoring.data && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('stopTitle')} />
            <div className="card-pad col" style={{ gap: 10 }}>
              {(['BLACKLIST', 'FRAUD', 'PASSPORT_EXPIRED', 'INCOME_NOT_VERIFIED', 'COURT', 'OVERDUE', 'OVERLIMIT'] as StopFactor[]).map((s) => {
                const hit = scoring.data!.stopFactors.includes(s);
                return (
                  <div key={s} className="row between" style={{ padding: '6px 0', borderBottom: '1px solid var(--border)' }}>
                    <span>{t(`s_${s}`)}</span>
                    {hit ? <Badge tone="danger" dot>!</Badge> : <Badge tone="success"><Icon name="check" size={14} /></Badge>}
                  </div>
                );
              })}
              <div className="divider" />
              <b style={{ fontSize: 13 }}>{t('manualStops')}</b>
              {MANUAL_STOPS.map((s) => (
                <label key={s} className="check-row">
                  <input type="checkbox" checked={manualStops.includes(s)}
                    onChange={(e) => setManualStops((m) => (e.target.checked ? [...m, s] : m.filter((x) => x !== s)))} />
                  {t(`s_${s}`)}
                </label>
              ))}
            </div>
          </Card>
          <Card>
            <div className="card-pad col center" style={{ gap: 14 }}>
              <Gauge value={scoring.data.total} cat={scoring.data.risk} size={160} label={t('scoreOf')} />
              {decision === 'APPROVED' && <Badge tone="success" dot>{t('approved')}</Badge>}
              {decision === 'REVIEW' && <Badge tone="warn" dot>{t('review')}</Badge>}
              {decision === 'REJECTED' && <Badge tone="danger" dot>{t('rejected')}</Badge>}
              {decision === 'REVIEW' && !can('CONTRACTS_APPROVE') && <span className="faint">{t('reviewNeedsAdmin')}</span>}
            </div>
          </Card>
        </div>
      )}

      {step === 3 && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('murabahaTitle')} sub={t('noInterest')} />
            <div className="card-pad col" style={{ gap: 16 }}>
              <Field label={t('cartAdd')} req>
                <div className="row" style={{ gap: 8 }}>
                  <select className="select" style={{ flex: 1 }} value={addId ?? ''} onChange={(e) => setAddId(Number(e.target.value) || null)}>
                    <option value="">—</option>
                    {products.data?.map((p) => (
                      <option key={p.id} value={p.id} disabled={p.stock <= 0}>
                        {p.name} · {fmtSom(p.price, lang)}{p.stock <= 0 ? ` · ${t('outOfStock')}` : ` · ${p.stock} ${t('pcs')}`}
                      </option>
                    ))}
                  </select>
                  <Btn icon="plus" disabled={addId == null} onClick={addToCart}>{t('add')}</Btn>
                </div>
              </Field>
              {lines.length > 0 && (
                <div className="cart-list">
                  {lines.map((l) => (
                    <div key={l.p.id} className="cart-line">
                      <div className="col" style={{ gap: 1, flex: 1, minWidth: 0 }}>
                        <b style={{ fontSize: 13 }}>{l.p.name}</b>
                        <span className="faint mono" style={{ fontSize: 11.5 }}>{fmtSom(l.p.price, lang)} · {t('stock')}: {l.p.stock}</span>
                      </div>
                      <input className="input cart-qty" type="number" min={1} max={l.p.stock} value={l.qty}
                        onChange={(e) => setQty(l.p.id, Number(e.target.value))} />
                      <b className="mono cart-sum">{fmtSom(l.total, lang)}</b>
                      <button className="iconbtn" title={t('remove')} onClick={() => updateCart(cart.filter((c) => c.productId !== l.p.id))}>
                        <Icon name="x" size={15} />
                      </button>
                    </div>
                  ))}
                  <div className="cart-line cart-total">
                    <b style={{ flex: 1 }}>{t('cartTotal')}</b><b className="mono">{fmtSom(cartTotal, lang)}</b>
                  </div>
                </div>
              )}
              {termConflict && <div className="stage-tip st-warn"><Icon name="alert" size={18} /><span>{t('termConflict', { min: termMin, max: termMax })}</span></div>}
              {lines.length > 0 && !termConflict && (
                <>
                  <Field label={`${t('markup')} · ${markupPct ?? defaultMarkup}%`}>
                    <input type="range" className="rng" min={0} max={50} value={markupPct ?? defaultMarkup}
                      onChange={(e) => setMarkupPct(Number(e.target.value))} />
                  </Field>
                  <Field label={`${t('term')} · ${termMonths} ${t('months')}`}>
                    <input type="range" className="rng" min={termMin} max={termMax} value={termMonths}
                      onChange={(e) => setTermMonths(Number(e.target.value))} />
                  </Field>
                  <Field label={t('downPayment')}>
                    <input className="input" type="number" min={0} value={downPayment}
                      onChange={(e) => setDownPayment(Number(e.target.value))} />
                  </Field>
                  <div className="form-grid">
                    <Field label={t('guarantorName')}>
                      <input className="input" value={guarantorName} onChange={(e) => setGuarantorName(e.target.value)} />
                    </Field>
                    <Field label={t('guarantorPhone')}>
                      <input className="input" value={guarantorPhone} onChange={(e) => setGuarantorPhone(e.target.value)} />
                    </Field>
                  </div>
                </>
              )}
              <ErrorBox error={quote.error} />
            </div>
          </Card>
          <div className="col" style={{ gap: 16, minWidth: 0 }}>
            {limit.data && quote.data && quote.data.monthlyPayment > limit.data.freeMonthly && (
              <div className="stage-tip st-warn">
                <Icon name="alert" size={18} />
                <span>{t('overCapacity', { monthly: fmtSom(quote.data.monthlyPayment, lang), free: fmtSom(limit.data.freeMonthly, lang) })}</span>
              </div>
            )}
            {limit.data && <LimitCard l={limit.data} compact />}
            <QuoteCard quote={quote.data} />
            {offers.data && <ProviderCard offers={offers.data} chosen={chosen} onChoose={setRoute} />}
          </div>
        </div>
      )}

      {step === 4 && quote.data && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('contractReady')} sub={client?.fullName} />
            <div className="card-pad">
              <dl className="kv">
                <dt>{t('client')}</dt><dd>{client?.fullName}</dd>
                <dt>{t('product')}</dt><dd>{lines.map((l) => `${l.p.name}${l.qty > 1 ? ` ×${l.qty}` : ''}`).join(', ')}</dd>
                <dt>{t('risk')}</dt><dd>{scoring.data?.risk} · {scoring.data?.total}</dd>
                <dt>{t('salePrice')}</dt><dd>{fmtSom(quote.data.salePrice, lang)}</dd>
                <dt>{t('monthly')}</dt><dd>{fmtSom(quote.data.monthlyPayment, lang)}</dd>
                <dt>{t('term')}</dt><dd>{quote.data.termMonths} {t('months')}</dd>
              </dl>
            </div>
            {offers.data && decision !== 'REJECTED' && (
              <div className="card-pad" style={{ paddingTop: 0 }}>
                <ProviderCard offers={offers.data} chosen={chosen} onChoose={setRoute} />
                {viaPartner && (
                  <Field label={t('partnerTx')} hint={t('partnerTxHint')}>
                    <input className="input" value={partnerTx} onChange={(e) => setPartnerTx(e.target.value)} maxLength={100} />
                  </Field>
                )}
              </div>
            )}
            <div className="card-pad col" style={{ gap: 10 }}>
              {decision === 'REJECTED' && <div className="stage-tip st-danger"><Icon name="alert" size={18} /><span>{t('wizRejectedNote')}</span></div>}
              {decision === 'REVIEW' && !can('CONTRACTS_APPROVE') && <div className="stage-tip st-warn"><Icon name="alert" size={18} /><span>{t('wizReviewNote')}</span></div>}
              <ErrorBox error={create.error} />
            </div>
          </Card>
          <QuoteCard quote={quote.data} />
        </div>
      )}

      <div className="row between" style={{ marginTop: 24 }}>
        <Btn variant="ghost" icon="chevL" onClick={() => (step === 0 ? nav(-1) : setStep(step - 1))}>
          {step === 0 ? t('cancel') : t('back')}
        </Btn>
        {step < 4
          ? <Btn disabled={!canNext} onClick={() => setStep(step + 1)}>{step === 3 ? t('proceed') : t('next')}</Btn>
          : <Btn variant={decision === 'REJECTED' ? 'danger' : 'gold'} icon="check" disabled={create.isPending} onClick={() => create.mutate()}>{t(finishKey)}</Btn>}
      </div>
    </div>
  );
}

function LimitCard({ l, compact }: { l: CreditLimitResult; compact?: boolean }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const usedPct = l.maxMonthly > 0 ? Math.min(100, (l.usedMonthly / l.maxMonthly) * 100) : 100;
  return (
    <div className="limit-card" style={{ width: '100%' }}>
      <div className="row between">
        <span className="faint" style={{ fontSize: 12 }}>{t('internalLimit')} · {t('months12')}</span>
        <b className="mono" style={{ color: l.limit > 0 ? 'var(--success)' : 'var(--danger)', fontSize: compact ? 15 : 18 }}>{fmtSom(l.limit, lang)}</b>
      </div>
      <div className="bar" style={{ margin: '8px 0' }} title={`${Math.round(usedPct)}%`}>
        <i style={{ width: `${usedPct}%`, background: usedPct >= 100 ? 'var(--danger)' : 'var(--gold)' }} />
      </div>
      <dl className="kv" style={{ fontSize: 12 }}>
        <dt>{t('maxMonthly')} (DTI {l.dtiPct}%)</dt><dd className="mono">{fmtSom(l.maxMonthly, lang)}</dd>
        <dt>{t('usedMonthly')}</dt><dd className="mono">{fmtSom(l.usedMonthly, lang)}</dd>
        <dt>{t('freeMonthly')}</dt><dd className="mono" style={{ color: 'var(--success)' }}>{fmtSom(l.freeMonthly, lang)}</dd>
      </dl>
    </div>
  );
}

function QuoteCard({ quote }: { quote?: Quote }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  if (!quote) return <Card><div className="card-pad faint">{t('selectProduct')}</div></Card>;
  return (
    <Card>
      <CardHead title={t('schedule')} sub={`${fmtSom(quote.monthlyPayment, lang)} × ${quote.termMonths}`} />
      <div className="card-pad" style={{ paddingBottom: 8 }}>
        <dl className="kv">
          <dt>{t('productPrice')}</dt><dd>{fmtSom(quote.costPrice, lang)}</dd>
          <dt>{t('downPayment')}</dt><dd>{fmtSom(quote.downPayment, lang)}</dd>
          <dt>{t('profit')} ({quote.markupPct}%)</dt><dd>{fmtSom(quote.markupAmount, lang)}</dd>
          <dt>{t('salePrice')}</dt><dd>{fmtSom(quote.salePrice, lang)}</dd>
          <dt>{t('installmentTotal')}</dt><dd>{fmtSom(quote.installmentTotal, lang)}</dd>
        </dl>
      </div>
      <div className="table-wrap" style={{ maxHeight: 300, overflowY: 'auto' }}>
        <table className="tbl tbl-plain">
          <tbody>
            {quote.schedule.map((s) => (
              <tr key={s.seq} style={{ cursor: 'default' }}>
                <td className="faint">{s.seq}</td>
                <td>{fmtDate(s.dueDate, lang)}</td>
                <td className="num">{fmtSom(s.amount, lang)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
}

/** Ichki murobaha va hamkor provayderlar (Alif / Uzum / TBC) solishtirmasi. */
function ProviderCard({ offers, chosen, onChoose }: { offers: PartnerOffers; chosen: string; onChoose: (code: string) => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const rec = offers.recommendation.provider;
  return (
    <Card>
      <CardHead title={t('providers')} sub={t(`rec_${offers.recommendation.reason}`)} />
      <div className="card-pad col" style={{ gap: 8 }}>
        <button className="prov-row" data-on={chosen === 'INTERNAL'} onClick={() => onChoose('INTERNAL')}>
          <div className="col" style={{ gap: 1, textAlign: 'left', flex: 1 }}>
            <b style={{ fontSize: 13 }}>{t('providerInternal')} {rec === 'INTERNAL' && <Badge tone="success">{t('recommended')}</Badge>}</b>
            <span className="faint" style={{ fontSize: 11.5 }}>{t('internalLimit')}: {fmtSom(offers.internal.limit, lang)}</span>
          </div>
          {offers.internal.covers ? <Badge tone="success"><Icon name="check" size={13} /></Badge> : <Badge tone="warn">{t('limitShort')}</Badge>}
        </button>
        {offers.partners.map((o) => (
          <button key={o.code} className="prov-row" data-on={chosen === o.code} disabled={!o.eligible} onClick={() => onChoose(o.code)}>
            <div className="col" style={{ gap: 1, textAlign: 'left', flex: 1 }}>
              <b style={{ fontSize: 13 }}>{o.name} {rec === o.code && <Badge tone="success">{t('recommended')}</Badge>}</b>
              <span className="faint" style={{ fontSize: 11.5 }}>
                {t('limit')}: {fmtSom(o.limit, lang)} · {o.maxTerm} {t('months')} · {t('commission')} {o.commissionPct}% ({fmtSom(o.commission, lang)})
              </span>
            </div>
            {o.eligible ? <Badge tone="success"><Icon name="check" size={13} /></Badge> : <Badge tone="danger">{t(`po_${o.reason}`)}</Badge>}
          </button>
        ))}
      </div>
    </Card>
  );
}
