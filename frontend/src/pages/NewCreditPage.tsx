import { Fragment, useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type {
  ApplicationDraft, ApplicationView, CartItem, Category, ClientDetails, ClientListItem, CreditLimitResult, Factor, Page, PartnerOrder,
  PaymentMethod, Product, Quote, QuoteRequest, ScoringRequest, ScoringResult, StopFactor, PartnerOffers, VerificationStatus,
} from '../api/types';
import { Avatar, Badge, Btn, Card, CardHead, ErrorBox, Field, Gauge, Icon, Loading } from '../components/ui';
import VerificationPanel from '../components/VerificationPanel';
import ExternalLimitsPanel from '../components/ExternalLimitsPanel';
import MibPanel from '../components/MibPanel';
import ClientForm from './ClientForm';
import { CreditReportsCard } from './ClientDetailPage';
import { useAuth } from '../lib/auth';
import { useBranch } from '../lib/branch';
import { fmtDate, fmtSom } from '../lib/format';

const STEPS = ['step_client', 'step_limit', 'step_cart', 'step_finalize'];
const FACTORS: { key: Factor; max: number }[] = [
  { key: 'INCOME', max: 30 }, { key: 'TENURE', max: 15 }, { key: 'FAMILY', max: 10 },
  { key: 'HISTORY', max: 20 }, { key: 'DISCIPLINE', max: 15 }, { key: 'GUARANTOR', max: 10 },
];
const ALL_STOPS: StopFactor[] = ['BLACKLIST', 'FRAUD', 'PASSPORT_EXPIRED', 'INCOME_NOT_VERIFIED', 'COURT', 'OVERDUE', 'OVERLIMIT', 'EMPLOYEE_RESTRICTED', 'MIB_DEBT'];
const MANUAL_STOPS: StopFactor[] = ['BLACKLIST', 'FRAUD', 'COURT'];
const METHODS: PaymentMethod[] = ['CASH', 'CARD', 'PAYME', 'CLICK', 'UZUM', 'BANK_TRANSFER'];

/** Konveyer holati — qoralama sifatida saqlanadi va keyin shu holatdan davom ettiriladi. */
interface WizardState {
  clientId: number | null;
  points: Record<Factor, number>;
  incomeVerified: boolean;
  guarantorPresent: boolean;
  manualStops: StopFactor[];
  cart: CartItem[];
  termMonths: number;
  markupPct?: number;
  downPayment: number;
  downPaymentMethod: PaymentMethod;
  guarantorName: string;
  guarantorPhone: string;
  route: string | null;
}

const EMPTY: WizardState = {
  clientId: null,
  points: { INCOME: 20, TENURE: 10, FAMILY: 7, HISTORY: 12, DISCIPLINE: 10, GUARANTOR: 5 },
  incomeVerified: true, guarantorPresent: false, manualStops: [], cart: [], termMonths: 12, downPayment: 0,
  downPaymentMethod: 'CASH', guarantorName: '', guarantorPhone: '', route: null,
};

/** ?draft=ID bo'lsa qoralama yuklanadi, ?clientId=ID bo'lsa mijoz oldindan tanlanadi. */
export default function NewCreditPage() {
  const [params] = useSearchParams();
  const draftId = params.get('draft') ? Number(params.get('draft')) : null;
  const draft = useQuery({
    queryKey: ['application-draft', draftId],
    queryFn: () => api.get<ApplicationDraft>(`/api/applications/drafts/${draftId}`),
    enabled: draftId != null, gcTime: 0,
  });
  if (draftId != null) {
    if (draft.error) return <div className="page"><ErrorBox error={draft.error} /></div>;
    if (!draft.data) return <Loading />;
    return <Wizard key={draftId} draftId={draftId} initialStep={Math.min(3, draft.data.step)}
      initial={{ ...EMPTY, ...(draft.data.payload as Partial<WizardState>), clientId: draft.data.clientId }} />;
  }
  return <Wizard initialStep={0} initial={{ ...EMPTY, clientId: params.get('clientId') ? Number(params.get('clientId')) : null }} />;
}

function Wizard({ initial, initialStep, draftId: initialDraftId }: { initial: WizardState; initialStep: number; draftId?: number }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const nav = useNavigate();
  const qc = useQueryClient();
  const { can, hasRole } = useAuth();
  const branch = useBranch();
  const [step, setStep] = useState(initialStep);
  const [draftId, setDraftId] = useState<number | null>(initialDraftId ?? null);

  // 1. mijoz
  const [clientId, setClientId] = useState<number | null>(initial.clientId);
  const [query, setQuery] = useState('');
  const [addingClient, setAddingClient] = useState(false);
  const clients = useQuery({
    queryKey: ['clients', query, 0],
    queryFn: () => api.get<Page<ClientListItem>>(`/api/clients${qs({ q: query, size: 30 })}`),
  });
  const clientInfo = useQuery({
    queryKey: ['client-details', clientId],
    queryFn: () => api.get<ClientDetails>(`/api/clients/${clientId}`),
    enabled: clientId != null,
  });

  // mijoz tasdiqlanganmi (sozlamadagi rejimga ko'ra); rejim NONE bo'lsa to'siq yo'q
  const verification = useQuery({
    queryKey: ['verification', clientId],
    queryFn: () => api.get<VerificationStatus>(`/api/verifications/clients/${clientId}/status`),
    enabled: clientId != null,
  });

  // 2. skoring va limit
  const [points, setPoints] = useState<Record<Factor, number>>(initial.points);
  const [incomeVerified, setIncomeVerified] = useState(initial.incomeVerified);
  const [guarantorPresent, setGuarantorPresent] = useState(initial.guarantorPresent);
  const [manualStops, setManualStops] = useState<StopFactor[]>(initial.manualStops);
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

  // 3. savatcha va shartlar
  const products = useQuery({ queryKey: ['products', 'active'], queryFn: () => api.get<Product[]>('/api/products?activeOnly=true') });
  const [cart, setCart] = useState<CartItem[]>(initial.cart);
  const [prodQuery, setProdQuery] = useState('');
  const [category, setCategory] = useState('');
  const [termMonths, setTermMonths] = useState(initial.termMonths);
  const [markupPct, setMarkupPct] = useState<number | undefined>(initial.markupPct);
  const [downPayment, setDownPayment] = useState(initial.downPayment);
  const [downPaymentMethod, setDownPaymentMethod] = useState<PaymentMethod>(initial.downPaymentMethod);
  const [limitOverride, setLimitOverride] = useState(false);
  const [overrideReason, setOverrideReason] = useState('');
  const [guarantorName, setGuarantorName] = useState(initial.guarantorName);
  const [guarantorPhone, setGuarantorPhone] = useState(initial.guarantorPhone);

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
  const cats = useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/api/categories') });
  const catName = (code: string) => {
    const c = cats.data?.find((x) => x.code === code);
    return c ? (lang === 'ru' ? c.nameRu : c.nameUz) : code;
  };
  const categories = useMemo(() => [...new Set((products.data ?? []).map((p) => p.categoryCode))].sort(), [products.data]);
  const shownProducts = (products.data ?? []).filter((p) =>
    (!category || p.categoryCode === category) && (!prodQuery.trim() || p.name.toLowerCase().includes(prodQuery.trim().toLowerCase())));

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
  const addToCart = (id: number) => {
    const p = products.data?.find((x) => x.id === id);
    if (!p || p.stock <= 0) return;
    const exist = cart.find((c) => c.productId === id);
    if (exist && exist.quantity >= p.stock) return;
    updateCart(exist
      ? cart.map((c) => (c.productId === id ? { ...c, quantity: c.quantity + 1 } : c))
      : [...cart, { productId: id, quantity: 1 }]);
  };
  const setQty = (id: number, q: number) => {
    const stock = products.data?.find((x) => x.id === id)?.stock ?? 1;
    updateCart(cart.map((c) => (c.productId === id ? { ...c, quantity: Math.max(1, Math.min(stock || 1, q || 1)) } : c)));
  };

  const quoteReq: QuoteRequest | null = lines.length && !termConflict
    ? { items: cart, termMonths, markupPct, downPayment: downPayment || 0 } : null;
  const quote = useQuery({
    queryKey: ['quote', quoteReq],
    queryFn: () => api.post<Quote>('/api/contracts/quote', quoteReq),
    enabled: !!quoteReq,
  });

  // moliyalashtiruvchi: ichki nasiya yoki hamkor (limitlar 2-qadamda, savatcha bilan qayta hisoblanadi)
  const [route, setRoute] = useState<string | null>(initial.route); // null — tavsiyaga ko'ra, 'INTERNAL' yoki hamkor kodi
  const offers = useQuery({
    queryKey: ['partner-offers', clientId, scoring.data?.risk, cartTotal, downPayment, termMonths, quote.data?.installmentTotal],
    queryFn: () => api.get<PartnerOffers>(`/api/partners/offers${qs({
      clientId, risk: scoring.data?.risk, goodsTotal: cartTotal, downPayment: downPayment || 0, term: termMonths,
      internalAmount: quoteReq ? quote.data?.installmentTotal : undefined,
    })}`),
    enabled: clientId != null && !!scoring.data && step >= 1,
    placeholderData: (prev) => prev,
  });
  const decision = scoring.data?.decision;
  const chosen = decision === 'REJECTED' ? 'INTERNAL' : route ?? offers.data?.recommendation.provider ?? 'INTERNAL';
  const viaPartner = chosen !== 'INTERNAL';
  const chosenOffer = offers.data?.partners.find((o) => o.code === chosen);

  const applicationBody = () => ({
    clientId, terms: quoteReq, scoring: scoringReq,
    guarantorName: guarantorName || undefined, guarantorPhone: guarantorPhone || undefined,
    downPaymentMethod,
    limitOverride: limitOverride || undefined,
    overrideReason: limitOverride ? (overrideReason || undefined) : undefined,
    branchId: branch.param,
  });
  const dropDraft = async () => {
    if (draftId == null) return;
    await api.del(`/api/applications/drafts/${draftId}`).catch(() => undefined);
    setDraftId(null);
    void qc.invalidateQueries({ queryKey: ['application-drafts'] });
  };

  // Ichki nasiya: ariza → (tasdiqlangan bo'lsa) darhol shartnoma
  const create = useMutation({
    mutationFn: async () => {
      const a = await api.post<ApplicationView>('/api/applications?contractNow=true', applicationBody());
      await dropDraft();
      return a;
    },
    onSuccess: (a) => nav(a.status === 'CONTRACTED' && a.contractId ? `/contracts/${a.contractId}` : `/applications?focus=${a.id}`),
  });

  // Hamkor orqali: ariza → hamkorda buyurtma (mijozga SMS kod) → kod bilan tasdiqlash va tovarni topshirish
  const [application, setApplication] = useState<ApplicationView | null>(null);
  const [order, setOrder] = useState<PartnerOrder | null>(null);
  const [partnerTx, setPartnerTx] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [serials, setSerials] = useState<Record<string, string>>({});
  const [handoverNotes, setHandoverNotes] = useState('');
  const [done, setDone] = useState<ApplicationView | null>(null);
  const openOrder = useMutation({
    mutationFn: async () => {
      const a = application ?? await api.post<ApplicationView>('/api/applications', applicationBody());
      setApplication(a);
      await dropDraft();
      if (a.status !== 'APPROVED') return { a, o: null };
      const o = await api.post<PartnerOrder>(`/api/applications/${a.id}/partner-order`, { partnerCode: chosen });
      return { a, o };
    },
    onSuccess: ({ a, o }) => {
      if (!o) { nav(`/applications?focus=${a.id}`); return; }
      setOrder(o);
      setPartnerTx(o.transactionId);
    },
  });
  // har bir dona uchun alohida seriya raqami / IMEI
  const units = lines.flatMap((l) => Array.from({ length: l.qty }, (_, i) => ({
    key: `${l.p.id}-${i}`, name: l.qty > 1 ? `${l.p.name} #${i + 1}` : l.p.name,
  })));
  const handover = useMutation({
    mutationFn: () => api.post<ApplicationView>(`/api/applications/${application!.id}/handover`, {
      partnerCode: chosen, transactionId: partnerTx || undefined, smsCode: smsCode || undefined,
      notes: handoverNotes || undefined,
      serials: units.map((u) => ({ name: u.name, serial: serials[u.key] ?? '' })),
    }),
    onSuccess: (a) => { setDone(a); void qc.invalidateQueries({ queryKey: ['applications'] }); },
  });

  // qoralama
  const summary = lines.map((l) => `${l.p.name}${l.qty > 1 ? ` ×${l.qty}` : ''}`).join(', ').slice(0, 290);
  const saveDraft = useMutation({
    mutationFn: () => {
      const payload: WizardState = {
        clientId, points, incomeVerified, guarantorPresent, manualStops, cart, termMonths, markupPct,
        downPayment, downPaymentMethod, guarantorName, guarantorPhone, route,
      };
      const body = { clientId, step, summary: summary || undefined, amount: quote.data?.installmentTotal ?? (cartTotal || undefined), payload };
      return draftId != null
        ? api.put<ApplicationDraft>(`/api/applications/drafts/${draftId}`, body)
        : api.post<ApplicationDraft>('/api/applications/drafts', body);
    },
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['application-drafts'] }); nav('/applications?tab=drafts'); },
  });

  const client = clientInfo.data?.client;
  const overCapacity = !viaPartner && !!limit.data && !!quote.data && quote.data.monthlyPayment > limit.data.freeMonthly;
  const overrideOk = !overCapacity || (hasRole('ADMIN') && limitOverride && overrideReason.trim().length > 0);
  const partnerOk = !viaPartner || !!chosenOffer?.eligible;
  const canNext = [
    clientId != null && (verification.data?.satisfied ?? false),
    !!scoring.data,
    !!quote.data && !!quoteReq && overrideOk && partnerOk,
    true,
  ][step];
  const finishKey = decision === 'REJECTED' ? 'finishReject'
    : decision === 'REVIEW' && !can('CONTRACTS_APPROVE') ? 'finishReview' : 'finish';
  // limit ishlatilishi: ichki — oylik to'lov / bo'sh oylik quvvat; hamkor — moliyalashtiriladigan summa / hamkor limiti
  const financed = Math.max(0, cartTotal - (downPayment || 0));
  const usage = viaPartner
    ? { used: financed, cap: chosenOffer?.limit ?? 0 }
    : { used: quote.data && quoteReq ? quote.data.monthlyPayment : 0, cap: limit.data?.freeMonthly ?? 0 };
  const usagePct = usage.cap > 0 ? Math.min(100, (usage.used / usage.cap) * 100) : usage.used > 0 ? 100 : 0;

  if (done) {
    return (
      <div className="page anim" style={{ maxWidth: 720 }}>
        <Card>
          <div className="card-pad col center" style={{ gap: 14, textAlign: 'center', padding: '36px 24px' }}>
            <span className="wiz-dot" style={{ width: 56, height: 56, background: 'var(--success)', color: '#fff' }}><Icon name="check" size={28} /></span>
            <h2 style={{ margin: 0, fontSize: 20 }}>{t('wizDoneTitle')}</h2>
            <span className="faint">{t('wizDoneSub', { no: done.applicationNo })}</span>
            <dl className="kv" style={{ width: '100%', maxWidth: 440, textAlign: 'left' }}>
              <dt>{t('client')}</dt><dd>{done.clientName}</dd>
              <dt>{t('wizDonePartner')}</dt><dd>{done.partnerName}</dd>
              <dt>{t('partnerTx')}</dt><dd className="mono">{done.partnerTxId ?? '—'}</dd>
              <dt>{t('wizDoneValue')}</dt><dd className="mono">{fmtSom(done.costPrice, lang)}</dd>
              <dt>{t('commission')}</dt><dd className="mono">{fmtSom(done.partnerCommission, lang)}</dd>
            </dl>
            <div className="stage-tip st-info" style={{ marginTop: 0 }}><Icon name="shield" size={18} /><span>{t('wizDoneNote')}</span></div>
            <Btn icon="check" onClick={() => nav(`/applications?focus=${done.id}`)}>{t('wizBackToList')}</Btn>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="page anim" style={{ maxWidth: 1080 }}>
      <div className="card card-pad" style={{ marginBottom: 22 }}>
        <div className="wiz-steps">
          {STEPS.map((s, i) => (
            <Fragment key={s}>
              <button className="wiz-step" data-state={i === step ? 'active' : i < step ? 'done' : 'todo'}
                onClick={() => i < step && !application && setStep(i)}>
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
          <CardHead title={t('selectClient')}
            right={can('CLIENTS_EDIT') && <Btn size="sm" variant="soft" icon="plus" onClick={() => setAddingClient(true)}>{t('wizNewClient')}</Btn>} />
          <div className="card-pad" style={{ paddingTop: 14, paddingBottom: 14 }}>
            <div className="search" style={{ minWidth: 0, width: '100%' }}>
              <Icon name="search" /><input placeholder={t('search')} value={query} onChange={(e) => setQuery(e.target.value)} />
            </div>
          </div>
          <div className="scroll" style={{ maxHeight: 420, overflowY: 'auto', padding: '0 12px 12px' }}>
            {client && !clients.data?.content.some((c) => c.id === client.id) && (
              <button className="client-pick" data-on="true">
                <Avatar name={client.fullName} size={38} />
                <div className="col" style={{ gap: 1, minWidth: 0, flex: 1, textAlign: 'left' }}>
                  <b style={{ fontSize: 14 }}>{client.fullName}</b>
                  <span className="faint mono" style={{ fontSize: 11.5 }}>{client.pinfl} · {client.phone}</span>
                </div>
                <Icon name="check" />
              </button>
            )}
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
            {clients.data && clients.data.content.length === 0 && (
              <div className="col center" style={{ gap: 8, padding: 24 }}>
                <span className="faint">{t('wizClientNotFound')}</span>
                {can('CLIENTS_EDIT') && <Btn size="sm" icon="plus" onClick={() => setAddingClient(true)}>{t('wizNewClient')}</Btn>}
              </div>
            )}
          </div>
        </Card>
      )}
      {step === 0 && clientId != null && <div style={{ marginTop: 16 }}><VerificationPanel clientId={clientId} /></div>}
      {step === 0 && clientId != null && <div style={{ marginTop: 16 }}><MibPanel clientId={clientId} auto /></div>}
      {step === 0 && clientId != null && <div style={{ marginTop: 16 }}><ExternalLimitsPanel key={clientId} clientId={clientId} /></div>}
      {step === 0 && clientId != null && <CreditReportsCard clientId={clientId} />}
      {addingClient && <ClientForm onClose={() => setAddingClient(false)} onSaved={(c) => { setClientId(c.id); setQuery(''); }} />}

      {step === 1 && (
        <div className="dash-grid">
          <div className="col" style={{ gap: 16, minWidth: 0 }}>
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
            {scoring.data && (
              <Card>
                <CardHead title={t('stopTitle')} />
                <div className="card-pad col" style={{ gap: 10 }}>
                  {ALL_STOPS.map((s) => {
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
            )}
          </div>
          <div className="col" style={{ gap: 16, minWidth: 0 }}>
            <Card>
              <div className="card-pad col center" style={{ gap: 12 }}>
                {scoring.data && <Gauge value={scoring.data.total} cat={scoring.data.risk}
                  label={t('scoreOf')} sublabel={`${t('riskCategory')} ${scoring.data.risk}`} />}
                {decision === 'APPROVED' && <Badge tone="success" dot>{t('approved')}</Badge>}
                {decision === 'REVIEW' && <Badge tone="warn" dot>{t('review')}</Badge>}
                {decision === 'REJECTED' && <Badge tone="danger" dot>{t('rejected')}</Badge>}
                {decision === 'REVIEW' && !can('CONTRACTS_APPROVE') && <span className="faint">{t('reviewNeedsAdmin')}</span>}
                <ErrorBox error={scoring.error} />
                {limit.data && <LimitCard l={limit.data} />}
              </div>
            </Card>
            {offers.data && decision !== 'REJECTED' && <ProviderCard offers={offers.data} chosen={chosen} onChoose={setRoute} limitsOnly={cartTotal === 0} />}
            <ErrorBox error={offers.error} />
          </div>
        </div>
      )}

      {step === 2 && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('wizProducts')} sub={t('wizProductsSub')} />
            <div className="card-pad row" style={{ gap: 8, paddingTop: 14, paddingBottom: 10 }}>
              <div className="search" style={{ flex: 1, minWidth: 0 }}>
                <Icon name="search" /><input placeholder={t('search')} value={prodQuery} onChange={(e) => setProdQuery(e.target.value)} />
              </div>
              <select className="select" style={{ width: 'auto' }} value={category} onChange={(e) => setCategory(e.target.value)}>
                <option value="">{t('wizAllCategories')}</option>
                {categories.map((c) => <option key={c} value={c}>{catName(c)}</option>)}
              </select>
            </div>
            <div className="scroll" style={{ maxHeight: 520, overflowY: 'auto', padding: '0 12px 12px' }}>
              {shownProducts.map((p) => {
                const inCart = cart.find((c) => c.productId === p.id)?.quantity ?? 0;
                return (
                  <button key={p.id} className="client-pick" data-on={inCart > 0} disabled={p.stock <= 0} onClick={() => addToCart(p.id)}
                    style={p.stock <= 0 ? { opacity: .5, cursor: 'not-allowed' } : undefined}>
                    <div className="col" style={{ gap: 1, minWidth: 0, flex: 1, textAlign: 'left' }}>
                      <b style={{ fontSize: 13.5 }}>{p.name}</b>
                      <span className="faint mono" style={{ fontSize: 11.5 }}>
                        {fmtSom(p.price, lang)} · {p.stock <= 0 ? t('outOfStock') : `${p.stock} ${t('pcs')}`} · {p.markupPct}% · {p.termMin}–{p.termMax} {t('months')}
                      </span>
                    </div>
                    {inCart > 0 ? <Badge tone="success">×{inCart}</Badge> : <Icon name="plus" size={16} />}
                  </button>
                );
              })}
              {shownProducts.length === 0 && <div className="faint" style={{ padding: 16 }}>{t('empty')}</div>}
            </div>
          </Card>
          <div className="col" style={{ gap: 16, minWidth: 0 }}>
            <Card>
              <CardHead title={t('wizCart')} sub={`${t('wizFunder')}: ${viaPartner ? chosenOffer?.name ?? chosen : t('providerInternal')}`} />
              <div className="card-pad col" style={{ gap: 16 }}>
                {lines.length === 0 && <span className="faint">{t('wizCartEmpty')}</span>}
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
                {lines.length > 0 && (
                  <div className="limit-card">
                    <div className="row between">
                      <span className="faint" style={{ fontSize: 12 }}>{t('wizLimitUsed')}</span>
                      <b className="mono" style={{ fontSize: 13, color: usagePct >= 100 && usage.used > usage.cap ? 'var(--danger)' : undefined }}>
                        {fmtSom(usage.used, lang)} / {fmtSom(usage.cap, lang)}
                      </b>
                    </div>
                    <div className="bar" style={{ marginTop: 8 }}>
                      <i style={{ width: `${usagePct}%`, background: usage.used > usage.cap ? 'var(--danger)' : 'var(--primary)' }} />
                    </div>
                    <span className="faint" style={{ fontSize: 11.5 }}>{t(viaPartner ? 'wizLimitPartnerHint' : 'wizLimitInternalHint')}</span>
                  </div>
                )}
                {termConflict && <div className="stage-tip st-warn"><Icon name="alert" size={18} /><span>{t('termConflict', { min: termMin, max: termMax })}</span></div>}
                {lines.length > 0 && !termConflict && (
                  <>
                    <Field label={t('downPayment')}>
                      <input className="input" type="number" min={0} value={downPayment}
                        onChange={(e) => setDownPayment(Number(e.target.value))} />
                    </Field>
                    <Field label={`${t('term')} · ${termMonths} ${t('months')}`}>
                      <input type="range" className="rng" min={termMin} max={termMax} value={termMonths}
                        onChange={(e) => setTermMonths(Number(e.target.value))} />
                    </Field>
                    {!viaPartner && (
                      <Field label={`${t('markup')} · ${markupPct ?? defaultMarkup}%`}>
                        <input type="range" className="rng" min={0} max={50} value={markupPct ?? defaultMarkup}
                          onChange={(e) => setMarkupPct(Number(e.target.value))} />
                      </Field>
                    )}
                    {quote.data && (
                      <dl className="kv" style={{ fontSize: 12.5 }}>
                        <dt>{t('wizFinanced')}</dt><dd className="mono">{fmtSom(financed, lang)}</dd>
                        {!viaPartner && <><dt>{t('profit')} ({quote.data.markupPct}%)</dt><dd className="mono">{fmtSom(quote.data.markupAmount, lang)}</dd></>}
                        {!viaPartner && <><dt>{t('monthly')}</dt><dd className="mono">{fmtSom(quote.data.monthlyPayment, lang)}</dd></>}
                        {viaPartner && chosenOffer && <><dt>{t('commission')} ({chosenOffer.commissionPct}%)</dt><dd className="mono">{fmtSom(chosenOffer.commission, lang)}</dd></>}
                      </dl>
                    )}
                  </>
                )}
                <ErrorBox error={quoteReq ? quote.error : null} />
              </div>
            </Card>
            {overCapacity && limit.data && quote.data && (
              <div className="stage-tip st-warn">
                <Icon name="alert" size={18} />
                <span>{t('overCapacity', { monthly: fmtSom(quote.data.monthlyPayment, lang), free: fmtSom(limit.data.freeMonthly, lang) })}</span>
              </div>
            )}
            {overCapacity && hasRole('ADMIN') && (
              <Card>
                <div className="card-pad col" style={{ gap: 10 }}>
                  <label className="check-row">
                    <input type="checkbox" checked={limitOverride}
                      onChange={(e) => setLimitOverride(e.target.checked)} />
                    {t('limitOverride')}
                  </label>
                  {limitOverride && (
                    <Field label={t('overrideReason')} req>
                      <input className="input" value={overrideReason}
                        onChange={(e) => setOverrideReason(e.target.value)} />
                    </Field>
                  )}
                </div>
              </Card>
            )}
            {viaPartner && chosenOffer && !chosenOffer.eligible && lines.length > 0 && (
              <div className="stage-tip st-danger"><Icon name="alert" size={18} /><span>{chosenOffer.name}: {t(`po_${chosenOffer.reason}`)}</span></div>
            )}
            {offers.data && decision !== 'REJECTED' && lines.length > 0 && <ProviderCard offers={offers.data} chosen={chosen} onChoose={setRoute} />}
          </div>
        </div>
      )}

      {step === 3 && quote.data && !viaPartner && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('contractReady')} sub={client?.fullName} />
            <div className="card-pad col" style={{ gap: 16 }}>
              <dl className="kv">
                <dt>{t('client')}</dt><dd>{client?.fullName}</dd>
                <dt>{t('product')}</dt><dd>{summary}</dd>
                <dt>{t('risk')}</dt><dd>{scoring.data?.risk} · {scoring.data?.total}</dd>
                <dt>{t('wizFunder')}</dt><dd>{t('providerInternal')}</dd>
                <dt>{t('salePrice')}</dt><dd>{fmtSom(quote.data.salePrice, lang)}</dd>
                <dt>{t('monthly')}</dt><dd>{fmtSom(quote.data.monthlyPayment, lang)}</dd>
                <dt>{t('term')}</dt><dd>{quote.data.termMonths} {t('months')}</dd>
              </dl>
              <Field label={t('downPaymentMethod')}>
                <select className="select" value={downPaymentMethod}
                  onChange={(e) => setDownPaymentMethod(e.target.value as PaymentMethod)}>
                  {METHODS.map((m) => <option key={m} value={m}>{t(`m_${m}`)}</option>)}
                </select>
              </Field>
              <div className="form-grid">
                <Field label={t('guarantorName')}>
                  <input className="input" value={guarantorName} onChange={(e) => setGuarantorName(e.target.value)} />
                </Field>
                <Field label={t('guarantorPhone')}>
                  <input className="input" value={guarantorPhone} onChange={(e) => setGuarantorPhone(e.target.value)} />
                </Field>
              </div>
              {decision === 'REJECTED' && <div className="stage-tip st-danger"><Icon name="alert" size={18} /><span>{t('wizRejectedNote')}</span></div>}
              {decision === 'REVIEW' && !can('CONTRACTS_APPROVE') && <div className="stage-tip st-warn"><Icon name="alert" size={18} /><span>{t('wizReviewNote')}</span></div>}
              <ErrorBox error={create.error} />
            </div>
          </Card>
          <QuoteCard quote={quote.data} />
        </div>
      )}

      {step === 3 && quote.data && viaPartner && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('wizPartnerTitle')} sub={`${chosenOffer?.name ?? chosen} · ${client?.fullName ?? ''}`} />
            <div className="card-pad col" style={{ gap: 16 }}>
              <div className="stage-tip st-info" style={{ marginTop: 0 }}><Icon name="shield" size={18} /><span>{t('wizPartnerFunded', { name: chosenOffer?.name ?? chosen })}</span></div>
              {decision === 'REVIEW' && !can('CONTRACTS_APPROVE') && !application && <div className="stage-tip st-warn"><Icon name="alert" size={18} /><span>{t('wizReviewNote')}</span></div>}
              {!order ? (
                <>
                  <span className="faint" style={{ fontSize: 12.5 }}>{t('wizOrderInfo')}</span>
                  <ErrorBox error={openOrder.error} />
                  <Btn icon="send" disabled={openOrder.isPending} onClick={() => openOrder.mutate()} style={{ alignSelf: 'flex-start' }}>
                    {openOrder.isPending ? t('wizOrderOpening') : t('wizOrderOpen')}
                  </Btn>
                </>
              ) : (
                <>
                  {order.mock && <div className="stage-tip st-warn" style={{ marginTop: 0 }}><Icon name="alert" size={18} /><span><b>{t('extLimMock')}</b> · {order.message}</span></div>}
                  <div className="form-grid">
                    <Field label={t('partnerTx')} req>
                      <input className="input mono" value={partnerTx} onChange={(e) => setPartnerTx(e.target.value)} maxLength={100} />
                    </Field>
                    <Field label={t('wizSmsCode')} req hint={t('wizSmsHint')}>
                      <input className="input mono" inputMode="numeric" value={smsCode} maxLength={6}
                        onChange={(e) => setSmsCode(e.target.value.replace(/\D/g, ''))} />
                    </Field>
                  </div>
                  <b style={{ fontSize: 13 }}>{t('wizSerials')}</b>
                  <div className="cart-list">
                    {units.map((u) => (
                      <div key={u.key} className="cart-line">
                        <b style={{ fontSize: 13, flex: 1, minWidth: 0 }}>{u.name}</b>
                        <input className="input mono" style={{ maxWidth: 240 }} placeholder={t('wizSerial')} maxLength={60}
                          value={serials[u.key] ?? ''} onChange={(e) => setSerials((s) => ({ ...s, [u.key]: e.target.value }))} />
                      </div>
                    ))}
                  </div>
                  <Field label={t('wizHandoverNotes')}>
                    <input className="input" value={handoverNotes} maxLength={500} onChange={(e) => setHandoverNotes(e.target.value)} />
                  </Field>
                  <ErrorBox error={handover.error} />
                </>
              )}
            </div>
          </Card>
          <Card>
            <CardHead title={t('wizHandoverSummary')} sub={application?.applicationNo} />
            <div className="card-pad">
              <dl className="kv">
                <dt>{t('wizReceiver')}</dt><dd>{client?.fullName}</dd>
                <dt>{t('product')}</dt><dd>{summary}</dd>
                <dt>{t('wizDoneValue')}</dt><dd className="mono">{fmtSom(cartTotal, lang)}</dd>
                <dt>{t('downPayment')}</dt><dd className="mono">{fmtSom(downPayment || 0, lang)}</dd>
                <dt>{t('wizFinanced')}</dt><dd className="mono">{fmtSom(financed, lang)}</dd>
                <dt>{t('term')}</dt><dd>{termMonths} {t('months')}</dd>
                {chosenOffer && <><dt>{t('commission')} ({chosenOffer.commissionPct}%)</dt><dd className="mono">{fmtSom(chosenOffer.commission, lang)}</dd></>}
              </dl>
            </div>
          </Card>
        </div>
      )}

      <ErrorBox error={saveDraft.error} />
      <div className="row between" style={{ marginTop: 24, gap: 10, flexWrap: 'wrap' }}>
        <Btn variant="ghost" icon="chevL" disabled={!!application} onClick={() => (step === 0 ? nav(-1) : setStep(step - 1))}>
          {step === 0 ? t('cancel') : t('back')}
        </Btn>
        <div className="row" style={{ gap: 10 }}>
          {clientId != null && !application && (
            <Btn variant="ghost" icon="doc" disabled={saveDraft.isPending} onClick={() => saveDraft.mutate()}>{t('wizSaveDraft')}</Btn>
          )}
          {step < 3 && <Btn disabled={!canNext} onClick={() => setStep(step + 1)}>{step === 2 ? t('proceed') : t('next')}</Btn>}
          {step === 3 && !viaPartner && (
            <Btn variant={decision === 'REJECTED' ? 'danger' : 'gold'} icon="check" disabled={create.isPending} onClick={() => create.mutate()}>{t(finishKey)}</Btn>
          )}
          {step === 3 && viaPartner && order && (
            <Btn variant="gold" icon="check" disabled={handover.isPending || !partnerTx.trim() || smsCode.length < 4} onClick={() => handover.mutate()}>{t('wizHandoverFinish')}</Btn>
          )}
        </div>
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

/** Ichki nasiya va hamkor provayderlar solishtirmasi. limitsOnly — savatcha hali bo'sh: faqat limitlar ko'rsatiladi. */
function ProviderCard({ offers, chosen, onChoose, limitsOnly }: { offers: PartnerOffers; chosen: string; onChoose: (code: string) => void; limitsOnly?: boolean }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const rec = offers.recommendation.provider;
  return (
    <Card>
      <CardHead title={t('providers')} sub={limitsOnly ? t('wizProviderPick') : t(`rec_${offers.recommendation.reason}`)} />
      <div className="card-pad col" style={{ gap: 8 }}>
        <button className="prov-row" data-on={chosen === 'INTERNAL'} onClick={() => onChoose('INTERNAL')}>
          <div className="col" style={{ gap: 1, textAlign: 'left', flex: 1 }}>
            <b style={{ fontSize: 13 }}>{t('providerInternal')} {rec === 'INTERNAL' && <Badge tone="success">{t('recommended')}</Badge>}</b>
            <span className="faint" style={{ fontSize: 11.5 }}>{t('internalLimit')}: {fmtSom(offers.internal.limit, lang)}</span>
          </div>
          {offers.internal.covers ? <Badge tone="success"><Icon name="check" size={13} /></Badge> : <Badge tone="warn">{t('limitShort')}</Badge>}
        </button>
        {offers.partners.map((o) => (
          <button key={o.code} className="prov-row" data-on={chosen === o.code} disabled={!o.eligible && !limitsOnly && chosen !== o.code} onClick={() => onChoose(o.code)}>
            <div className="col" style={{ gap: 1, textAlign: 'left', flex: 1 }}>
              <b style={{ fontSize: 13 }}>{o.name} {rec === o.code && <Badge tone="success">{t('recommended')}</Badge>}</b>
              <span className="faint" style={{ fontSize: 11.5 }}>
                {t('limit')}: {fmtSom(o.limit, lang)} · {o.maxTerm} {t('months')} · {t('commission')} {o.commissionPct}%{!limitsOnly && ` (${fmtSom(o.commission, lang)})`}
              </span>
            </div>
            {o.eligible || limitsOnly ? <Badge tone={o.limit > 0 ? 'success' : 'warn'}>{o.limit > 0 ? <Icon name="check" size={13} /> : t('limitShort')}</Badge> : <Badge tone="danger">{t(`po_${o.reason}`)}</Badge>}
          </button>
        ))}
      </div>
    </Card>
  );
}
