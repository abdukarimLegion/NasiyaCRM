import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api } from '../api/client';
import type { OrderStatus, PurchaseOrder, StockStatus, Supplier, WarehouseAnalytics, WarehouseProduct } from '../api/types';
import { Badge, Btn, Card, CardHead, Empty, ErrorBox, Field, Icon, Loading, Modal, Seg, Stat, type Tone } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtDate, fmtMln, fmtSom } from '../lib/format';

const STATUS_TONE: Record<StockStatus, Tone> = { OUT: 'danger', CRITICAL: 'danger', LOW: 'warn', OK: 'success', OVER: 'info', DEAD: 'neutral' };
const ORDER_TONE: Record<OrderStatus, Tone> = { DRAFT: 'neutral', APPROVED: 'info', RECEIVED: 'success', CANCELLED: 'danger' };
const FILTERS: (StockStatus | '' | 'ORDER')[] = ['', 'ORDER', 'OUT', 'CRITICAL', 'LOW', 'DEAD', 'OVER', 'OK'];

export default function WarehousePage() {
  const { t } = useTranslation();
  const [tab, setTab] = useState<'stock' | 'orders' | 'suppliers'>('stock');
  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="box" size={18} /></span>{t('warehouseTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>{t('warehouseSub')}</span>
        </div>
        <Seg value={tab} onChange={setTab} options={[
          { value: 'stock', label: t('whStock') }, { value: 'orders', label: t('whOrders') }, { value: 'suppliers', label: t('suppliers') },
        ]} />
      </div>
      {tab === 'stock' && <StockTab />}
      {tab === 'orders' && <OrdersTab />}
      {tab === 'suppliers' && <SuppliersTab />}
    </div>
  );
}

/* ------------------------------------------------------------------ qoldiq va tavsiyalar */

function StockTab() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const qc = useQueryClient();
  const [cover, setCover] = useState('30');
  const [filter, setFilter] = useState<StockStatus | '' | 'ORDER'>('');
  const [draft, setDraft] = useState<{ supplierId?: number; items: WarehouseProduct[] } | null>(null);
  const q = useQuery({
    queryKey: ['warehouse', cover],
    queryFn: () => api.get<WarehouseAnalytics>(`/api/warehouse/analytics?cover=${cover}`),
  });
  const rows = useMemo(() => (q.data?.products ?? []).filter((p) =>
    filter === '' ? true : filter === 'ORDER' ? p.recommendedQty > 0 : p.status === filter), [q.data, filter]);
  if (q.isLoading) return <Loading />;
  if (!q.data) return <ErrorBox error={q.error} />;
  const s = q.data.summary;
  const startDraft = (supplierId?: number) => {
    const items = q.data!.products.filter((p) => p.recommendedQty > 0 && (supplierId == null ? !p.supplierId : p.supplierId === supplierId));
    setDraft({ supplierId, items });
  };

  return (
    <>
      <div className="kpi4">
        <Stat icon="box" tone="info" label={t('whStockValue')} value={fmtMln(s.stockValue, lang)} sub={<>{s.units} {t('pcs')} · {s.products} {t('whSkus')}</>} />
        <Stat icon="alert" tone={s.out + s.critical > 0 ? 'danger' : 'success'} label={t('whRisk')} value={s.out + s.critical}
          sub={<>{t('st_OUT')}: <b>{s.out}</b> · {t('st_CRITICAL')}: <b>{s.critical}</b> · {t('st_LOW')}: <b>{s.low}</b></>} />
        <Stat icon="clock" tone="gold" label={t('whDead')} value={fmtMln(s.deadValue, lang)} sub={<>{s.dead} {t('whSkus')} · {t('whDeadHint')}</>} />
        <Stat icon="trend" tone="success" label={t('whTurnover')} value={s.turnoverPerYear != null ? `${s.turnoverPerYear}×` : '—'} sub={t('whTurnoverHint')} />
      </div>

      {q.data.suggestions.length > 0 && (
        <Card className="block">
          <CardHead title={<><Icon name="spark" size={16} /> {t('whReorder')}</>} sub={t('whReorderSub', { n: q.data.coverDays })}
            right={<Badge tone="info">{s.toOrderUnits} {t('pcs')} · {fmtMln(s.toOrderCost, lang)}</Badge>} />
          <div className="card-pad wh-sug">
            {q.data.suggestions.map((g) => (
              <div key={g.supplierId ?? 0} className="wh-sug-card">
                <div className="col" style={{ gap: 2, minWidth: 0 }}>
                  <b>{g.supplierName ?? t('whNoSupplier')}</b>
                  <span className="faint" style={{ fontSize: 12 }}>{g.lines} {t('whSkus')} · {g.units} {t('pcs')} · {t('whLead')}: {g.leadDays} {t('days')}</span>
                </div>
                <div className="row" style={{ gap: 10 }}>
                  <b className="mono">{fmtMln(g.cost, lang)}</b>
                  {can('PRODUCTS_EDIT') && <Btn size="sm" icon="plus" onClick={() => startDraft(g.supplierId)}>{t('whMakeOrder')}</Btn>}
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      <Card className="block">
        <div className="card-pad filter-bar">
          <div className="row gap-sm wrap">
            {FILTERS.map((f) => (
              <span key={f || 'all'} className="chip" data-on={filter === f} onClick={() => setFilter(f)}>
                {f === '' ? t('all') : f === 'ORDER' ? t('whNeedOrder') : t(`st_${f}`)}
              </span>
            ))}
          </div>
          <div style={{ marginLeft: 'auto' }} className="row">
            <span className="faint" style={{ fontSize: 12 }}>{t('whCoverDays')}:</span>
            <Seg value={cover} onChange={setCover} options={['14', '30', '45', '60'].map((v) => ({ value: v, label: v }))} />
          </div>
        </div>
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('productName')}</th><th>{t('supplier')}</th><th className="num">{t('stock')}</th>
              <th className="num">{t('whSold')}</th><th className="num">{t('whPerDay')}</th><th className="num">{t('whCover')}</th>
              <th>{t('status')}</th><th className="num">{t('whRecommended')}</th>
            </tr></thead>
            <tbody>
              {rows.map((p) => (
                <tr key={p.id} style={{ cursor: 'default' }}>
                  <td className="cell-main"><div className="col" style={{ gap: 1 }}><b>{p.name}</b><span className="faint mono" style={{ fontSize: 11 }}>{p.sku || p.category}</span></div></td>
                  <td className="muted" data-label={t('supplier')}>{p.supplierName ?? '—'}</td>
                  <td className="num mono" data-label={t('stock')}><b>{p.stock}</b>{p.incoming > 0 && <span className="faint"> +{p.incoming}</span>}</td>
                  <td className="num mono" data-label={t('whSold')}>{p.sold30} / {p.sold90}</td>
                  <td className="num mono" data-label={t('whPerDay')}>{p.perDay}</td>
                  <td className="num mono" data-label={t('whCover')}>{p.coverDays != null ? `${p.coverDays} ${t('days')}` : '—'}</td>
                  <td data-label={t('status')}><Badge tone={STATUS_TONE[p.status]} dot>{t(`st_${p.status}`)}</Badge></td>
                  <td className="num mono" data-label={t('whRecommended')}>{p.recommendedQty > 0 ? <b>{p.recommendedQty}</b> : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {rows.length === 0 && <Empty />}
      </Card>

      {draft && (
        <OrderDraftModal supplierId={draft.supplierId} items={draft.items} onClose={() => setDraft(null)}
          onDone={() => { setDraft(null); void qc.invalidateQueries({ queryKey: ['warehouse'] }); void qc.invalidateQueries({ queryKey: ['po'] }); }} />
      )}
    </>
  );
}

function OrderDraftModal({ supplierId, items, onClose, onDone }: {
  supplierId?: number; items: WarehouseProduct[]; onClose: () => void; onDone: () => void;
}) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const suppliers = useQuery({ queryKey: ['suppliers'], queryFn: () => api.get<Supplier[]>('/api/warehouse/suppliers') });
  const [sid, setSid] = useState<number | undefined>(supplierId);
  const [qty, setQty] = useState<Record<number, number>>(() => Object.fromEntries(items.map((i) => [i.id, i.recommendedQty])));
  const [notes, setNotes] = useState('');
  const lines = items.filter((i) => (qty[i.id] ?? 0) > 0);
  const total = lines.reduce((a, i) => a + (qty[i.id] ?? 0) * i.price, 0);
  const save = useMutation({
    mutationFn: () => api.post('/api/warehouse/orders', {
      supplierId: sid, notes: notes || undefined,
      items: lines.map((i) => ({ productId: i.id, quantity: qty[i.id] })),
    }),
    onSuccess: onDone,
  });
  return (
    <Modal wide title={t('whDraft')} onClose={onClose}
      footer={<><Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={!sid || lines.length === 0 || save.isPending} onClick={() => save.mutate()}>{t('whSaveDraft')}</Btn></>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <Field label={t('supplier')} req>
          <select className="select" value={sid ?? ''} onChange={(e) => setSid(e.target.value ? Number(e.target.value) : undefined)}>
            <option value="">—</option>
            {suppliers.data?.filter((x) => x.active).map((x) => <option key={x.id} value={x.id}>{x.name} · {x.leadTimeDays} {t('days')}</option>)}
          </select>
        </Field>
        <div className="table-wrap">
          <table className="tbl tbl-plain">
            <thead><tr><th>{t('productName')}</th><th className="num">{t('whPerDay')}</th><th className="num">{t('whQty')}</th><th className="num">{t('amount')}</th></tr></thead>
            <tbody>
              {items.map((i) => (
                <tr key={i.id} style={{ cursor: 'default' }}>
                  <td><b>{i.name}</b> <span className="faint">· {t('stock')}: {i.stock}</span></td>
                  <td className="num mono">{i.perDay}</td>
                  <td className="num"><input className="input cart-qty" type="number" min={0} value={qty[i.id] ?? 0}
                    onChange={(e) => setQty({ ...qty, [i.id]: Math.max(0, Number(e.target.value) || 0) })} /></td>
                  <td className="num mono">{fmtSom((qty[i.id] ?? 0) * i.price, lang)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="row between"><b>{t('cartTotal')}</b><b className="mono">{fmtSom(total, lang)}</b></div>
        <Field label={t('note')}><input className="input" value={notes} maxLength={500} onChange={(e) => setNotes(e.target.value)} /></Field>
      </div>
    </Modal>
  );
}

/* ------------------------------------------------------------------ buyurtmalar */

function OrdersTab() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const [open, setOpen] = useState<number | null>(null);
  const q = useQuery({ queryKey: ['po'], queryFn: () => api.get<PurchaseOrder[]>('/api/warehouse/orders') });
  return (
    <Card>
      <ErrorBox error={q.error} />
      <div className="table-wrap">
        <table className="tbl">
          <thead><tr><th>{t('whOrderNo')}</th><th>{t('supplier')}</th><th>{t('date')}</th><th>ETA</th><th>{t('status')}</th></tr></thead>
          <tbody>
            {q.data?.map((o) => (
              <tr key={o.id} onClick={() => setOpen(o.id)}>
                <td className="cell-main"><b className="mono">{o.orderNo}</b></td>
                <td data-label={t('supplier')}>{o.supplierName}</td>
                <td data-label={t('date')}>{fmtDate(o.createdAt, lang)}</td>
                <td data-label="ETA">{o.eta ? fmtDate(o.eta, lang) : '—'}</td>
                <td data-label={t('status')}><Badge tone={ORDER_TONE[o.status]} dot>{t(`po_${o.status}`)}</Badge></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {q.data && q.data.length === 0 && <Empty />}
      {open != null && <OrderModal id={open} onClose={() => setOpen(null)} />}
    </Card>
  );
}

function OrderModal({ id, onClose }: { id: number; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { can } = useAuth();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['po', id], queryFn: () => api.get<PurchaseOrder>(`/api/warehouse/orders/${id}`) });
  const act = useMutation({
    mutationFn: (a: 'approve' | 'receive' | 'cancel') => api.post<PurchaseOrder>(`/api/warehouse/orders/${id}/${a}`),
    onSuccess: (o) => {
      qc.setQueryData(['po', id], o);
      void qc.invalidateQueries({ queryKey: ['po'] });
      void qc.invalidateQueries({ queryKey: ['warehouse'] });
      void qc.invalidateQueries({ queryKey: ['products'] });
    },
  });
  const o = q.data;
  const print = () => {
    if (!o) return;
    const w = window.open('', '_blank');
    if (!w) return;
    const rows = o.items.map((i) => `<tr><td>${esc(i.productName)}</td><td>${esc(i.sku ?? '')}</td><td style="text-align:right">${i.quantity}</td><td style="text-align:right">${fmtSom(i.unitCost, lang)}</td><td style="text-align:right">${fmtSom(i.lineTotal, lang)}</td></tr>`).join('');
    w.document.write(`<html><head><title>${esc(o.orderNo)}</title><style>body{font:14px sans-serif;padding:24px}table{border-collapse:collapse;width:100%}td,th{border:1px solid #999;padding:6px 8px;text-align:left}</style></head><body>
      <h2>${esc(t('whOrderTitle'))} ${esc(o.orderNo)}</h2><p>${esc(t('supplier'))}: <b>${esc(o.supplierName)}</b><br>${esc(t('date'))}: ${fmtDate(o.createdAt, lang)} · ETA: ${o.eta ? fmtDate(o.eta, lang) : '—'}</p>
      <table><thead><tr><th>${esc(t('productName'))}</th><th>SKU</th><th>${esc(t('whQty'))}</th><th>${esc(t('price'))}</th><th>${esc(t('amount'))}</th></tr></thead><tbody>${rows}</tbody></table>
      <p style="text-align:right"><b>${esc(t('cartTotal'))}: ${fmtSom(o.total, lang)}</b></p><p>${esc(o.notes ?? '')}</p><script>window.print()</script></body></html>`);
    w.document.close();
  };
  return (
    <Modal wide title={o ? `${t('whOrderTitle')} ${o.orderNo}` : '…'} onClose={onClose}
      footer={o && (
        <>
          <Btn variant="ghost" icon="printer" onClick={print}>{t('print')}</Btn>
          {can('PRODUCTS_EDIT') && o.status !== 'RECEIVED' && o.status !== 'CANCELLED' && <Btn variant="ghost" icon="x" disabled={act.isPending} onClick={() => act.mutate('cancel')}>{t('cancelOrder')}</Btn>}
          {can('PRODUCTS_EDIT') && o.status === 'DRAFT' && <Btn icon="check" disabled={act.isPending} onClick={() => act.mutate('approve')}>{t('approve')}</Btn>}
          {can('PRODUCTS_EDIT') && o.status === 'APPROVED' && <Btn variant="gold" icon="box" disabled={act.isPending} onClick={() => act.mutate('receive')}>{t('whReceive')}</Btn>}
        </>
      )}>
      {!o ? <Loading /> : (
        <div className="col" style={{ gap: 14 }}>
          <ErrorBox error={act.error} />
          <div className="row between"><b>{o.supplierName}</b><Badge tone={ORDER_TONE[o.status]} dot>{t(`po_${o.status}`)}</Badge></div>
          <div className="table-wrap">
            <table className="tbl tbl-plain">
              <thead><tr><th>{t('productName')}</th><th className="num">{t('whQty')}</th><th className="num">{t('price')}</th><th className="num">{t('amount')}</th></tr></thead>
              <tbody>{o.items.map((i) => (
                <tr key={i.productId} style={{ cursor: 'default' }}><td>{i.productName}</td><td className="num mono">{i.quantity}</td>
                  <td className="num mono">{fmtSom(i.unitCost, lang)}</td><td className="num mono">{fmtSom(i.lineTotal, lang)}</td></tr>))}</tbody>
            </table>
          </div>
          <div className="row between"><b>{t('cartTotal')}</b><b className="mono">{fmtSom(o.total, lang)}</b></div>
          <dl className="kv">
            {o.eta && <><dt>ETA</dt><dd>{fmtDate(o.eta, lang)}</dd></>}
            {o.createdBy && <><dt>{t('officer')}</dt><dd>{o.createdBy}</dd></>}
            {o.approvedBy && <><dt>{t('decidedBy')}</dt><dd>{o.approvedBy}</dd></>}
            {o.notes && <><dt>{t('note')}</dt><dd>{o.notes}</dd></>}
          </dl>
        </div>
      )}
    </Modal>
  );
}

function esc(s: string) {
  return s.replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c] as string));
}

/* ------------------------------------------------------------------ yetkazib beruvchilar */

function SuppliersTab() {
  const { t } = useTranslation();
  const { can } = useAuth();
  const [edit, setEdit] = useState<Supplier | 'new' | null>(null);
  const q = useQuery({ queryKey: ['suppliers'], queryFn: () => api.get<Supplier[]>('/api/warehouse/suppliers') });
  return (
    <Card>
      <CardHead title={t('suppliers')} sub={t('suppliersSub')} right={can('PRODUCTS_EDIT') && <Btn size="sm" icon="plus" onClick={() => setEdit('new')}>{t('addSupplier')}</Btn>} />
      <ErrorBox error={q.error} />
      <div className="table-wrap">
        <table className="tbl">
          <thead><tr><th>{t('name')}</th><th>{t('phone')}</th><th>{t('whContact')}</th><th className="num">{t('whLead')}</th><th>{t('status')}</th></tr></thead>
          <tbody>
            {q.data?.map((s) => (
              <tr key={s.id} onClick={() => can('PRODUCTS_EDIT') && setEdit(s)} style={{ opacity: s.active ? 1 : 0.55 }}>
                <td className="cell-main"><b>{s.name}</b></td>
                <td className="mono" data-label={t('phone')}>{s.phone ?? '—'}</td>
                <td data-label={t('whContact')}>{s.contactPerson ?? '—'}</td>
                <td className="num mono" data-label={t('whLead')}>{s.leadTimeDays} {t('days')}</td>
                <td data-label={t('status')}>{s.active ? <Badge tone="success" dot>{t('active')}</Badge> : <Badge>{t('inactive')}</Badge>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {q.data && q.data.length === 0 && <Empty />}
      {edit && <SupplierModal supplier={edit === 'new' ? null : edit} onClose={() => setEdit(null)} />}
    </Card>
  );
}

function SupplierModal({ supplier, onClose }: { supplier: Supplier | null; onClose: () => void }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [d, setD] = useState<Omit<Supplier, 'id'>>(supplier ?? { name: '', leadTimeDays: 7, active: true });
  const save = useMutation({
    mutationFn: () => (supplier ? api.put(`/api/warehouse/suppliers/${supplier.id}`, d) : api.post('/api/warehouse/suppliers', d)),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['suppliers'] }); onClose(); },
  });
  return (
    <Modal title={supplier ? supplier.name : t('addSupplier')} onClose={onClose}
      footer={<><Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn><Btn icon="check" disabled={!d.name || save.isPending} onClick={() => save.mutate()}>{t('save')}</Btn></>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <Field label={t('name')} req><input className="input" value={d.name} onChange={(e) => setD({ ...d, name: e.target.value })} /></Field>
        <div className="form-grid">
          <Field label={t('phone')}><input className="input" value={d.phone ?? ''} onChange={(e) => setD({ ...d, phone: e.target.value })} /></Field>
          <Field label={t('whContact')}><input className="input" value={d.contactPerson ?? ''} onChange={(e) => setD({ ...d, contactPerson: e.target.value })} /></Field>
          <Field label={`${t('whLead')} (${t('days')})`}><input className="input" type="number" min={0} max={180} value={d.leadTimeDays} onChange={(e) => setD({ ...d, leadTimeDays: Number(e.target.value) })} /></Field>
        </div>
        <Field label={t('note')}><input className="input" value={d.note ?? ''} maxLength={500} onChange={(e) => setD({ ...d, note: e.target.value })} /></Field>
        <label className="check-row"><input type="checkbox" checked={d.active} onChange={(e) => setD({ ...d, active: e.target.checked })} />{t('active')}</label>
      </div>
    </Modal>
  );
}
