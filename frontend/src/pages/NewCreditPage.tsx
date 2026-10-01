import { Fragment, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type {
  Client, ContractDetails, Factor, Page, Product, Quote, QuoteRequest, ScoringRequest, ScoringResult, StopFactor,
} from '../api/types';
import { Avatar, Badge, Btn, Card, CardHead, ErrorBox, Field, Gauge, Icon } from '../components/ui';
import { useAuth } from '../lib/auth';
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
  const { hasRole } = useAuth();
  const [params] = useSearchParams();
  const [step, setStep] = useState(0);

  // 1. mijoz
  const [clientId, setClientId] = useState<number | null>(params.get('clientId') ? Number(params.get('clientId')) : null);
  const [query, setQuery] = useState('');
  const clients = useQuery({
    queryKey: ['clients', query, 0],
    queryFn: () => api.get<Page<Client>>(`/api/clients${qs({ q: query, size: 30 })}`),
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

  // 4. murobaha
  const products = useQuery({ queryKey: ['products', 'active'], queryFn: () => api.get<Product[]>('/api/products?activeOnly=true') });
  const [productId, setProductId] = useState<number | null>(null);
  const product = products.data?.find((p) => p.id === productId);
  const [termMonths, setTermMonths] = useState(12);
  const [markupPct, setMarkupPct] = useState<number | undefined>();
  const [downPayment, setDownPayment] = useState(0);
  const [guarantorName, setGuarantorName] = useState('');
  const [guarantorPhone, setGuarantorPhone] = useState('');

  const selectProduct = (id: number | null) => {
    setProductId(id);
    const p = products.data?.find((x) => x.id === id);
    if (p) {
      setMarkupPct(p.markupPct);
      setTermMonths((m) => Math.min(p.termMax, Math.max(p.termMin, m)));
    }
  };

  const quoteReq: QuoteRequest | null = product
    ? { productId: product.id, termMonths, markupPct, downPayment: downPayment || 0 } : null;
  const quote = useQuery({
    queryKey: ['quote', quoteReq],
    queryFn: () => api.post<Quote>('/api/contracts/quote', quoteReq),
    enabled: !!quoteReq,
  });

  const create = useMutation({
    mutationFn: () => api.post<ContractDetails>('/api/contracts', {
      clientId, terms: quoteReq, scoring: scoringReq,
      guarantorName: guarantorName || undefined, guarantorPhone: guarantorPhone || undefined,
    }),
    onSuccess: (c) => nav(`/contracts/${c.id}`),
  });

  const client = useMemo(() => clients.data?.content.find((c) => c.id === clientId), [clients.data, clientId]);
  const decision = scoring.data?.decision;
  const canNext = [
    clientId != null,
    !!scoring.data,
    decision === 'APPROVED' || (decision === 'REVIEW' && hasRole('ADMIN')),
    !!quote.data,
    true,
  ][step];

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
              {decision === 'REVIEW' && !hasRole('ADMIN') && <span className="faint">{t('reviewNeedsAdmin')}</span>}
            </div>
          </Card>
        </div>
      )}

      {step === 3 && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('murabahaTitle')} sub={t('noInterest')} />
            <div className="card-pad col" style={{ gap: 16 }}>
              <Field label={t('selectProduct')} req>
                <select className="select" value={productId ?? ''} onChange={(e) => selectProduct(Number(e.target.value) || null)}>
                  <option value="">—</option>
                  {products.data?.map((p) => (
                    <option key={p.id} value={p.id} disabled={p.stock <= 0}>
                      {p.name} · {fmtSom(p.price, lang)}{p.stock <= 0 ? ` · ${t('outOfStock')}` : ''}
                    </option>
                  ))}
                </select>
              </Field>
              {product && (
                <>
                  <Field label={`${t('markup')} · ${markupPct ?? product.markupPct}%`}>
                    <input type="range" className="rng" min={0} max={50} value={markupPct ?? product.markupPct}
                      onChange={(e) => setMarkupPct(Number(e.target.value))} />
                  </Field>
                  <Field label={`${t('term')} · ${termMonths} ${t('months')}`}>
                    <input type="range" className="rng" min={product.termMin} max={product.termMax} value={termMonths}
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
          <QuoteCard quote={quote.data} />
        </div>
      )}

      {step === 4 && quote.data && (
        <div className="dash-grid">
          <Card>
            <CardHead title={t('contractReady')} sub={client?.fullName} />
            <div className="card-pad">
              <dl className="kv">
                <dt>{t('client')}</dt><dd>{client?.fullName}</dd>
                <dt>{t('product')}</dt><dd>{product?.name}</dd>
                <dt>{t('risk')}</dt><dd>{scoring.data?.risk} · {scoring.data?.total}</dd>
                <dt>{t('salePrice')}</dt><dd>{fmtSom(quote.data.salePrice, lang)}</dd>
                <dt>{t('monthly')}</dt><dd>{fmtSom(quote.data.monthlyPayment, lang)}</dd>
                <dt>{t('term')}</dt><dd>{quote.data.termMonths} {t('months')}</dd>
              </dl>
            </div>
            <div className="card-pad"><ErrorBox error={create.error} /></div>
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
          : <Btn variant="gold" icon="check" disabled={create.isPending} onClick={() => create.mutate()}>{t('finish')}</Btn>}
      </div>
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
        <table className="tbl">
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
