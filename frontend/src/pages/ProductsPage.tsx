import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { api, qs } from '../api/client';
import type { Category, Product } from '../api/types';
import { Badge, Btn, Card, Empty, ErrorBox, Field, Icon, Modal } from '../components/ui';
import { useAuth } from '../lib/auth';
import { fmtSom } from '../lib/format';

type Draft = Omit<Product, 'id' | 'categoryCode'>;

/** Ombor qoldig'i shundan kam bo'lsa "kam qoldi" belgisi chiqadi */
const LOW_STOCK = 3;

export default function ProductsPage() {
  const { t, i18n } = useTranslation();
  const lang = i18n.language;
  const { hasRole } = useAuth();
  const [cat, setCat] = useState<number | ''>('');
  const [edit, setEdit] = useState<Product | 'new' | null>(null);
  const cats = useQuery({ queryKey: ['categories'], queryFn: () => api.get<Category[]>('/api/categories') });
  const { data, error } = useQuery({
    queryKey: ['products', cat],
    queryFn: () => api.get<Product[]>(`/api/products${qs({ categoryId: cat })}`),
  });
  const catName = (id: number) => {
    const c = cats.data?.find((x) => x.id === id);
    return c ? (lang === 'ru' ? c.nameRu : c.nameUz) : '';
  };

  return (
    <div className="page">
      <div className="page-head">
        <div className="col" style={{ gap: 2 }}>
          <h1 className="page-h1"><span className="h1-ic"><Icon name="briefcase" size={18} /></span>{t('catalogTitle')}</h1>
          <span className="faint" style={{ fontSize: 12.5 }}>
            {t('catalogSub')}{data ? ` · ${data.length} ${t('pcs')} · ${t('stockTotal')}: ${data.reduce((a, p) => a + p.stock, 0)}` : ''}
          </span>
        </div>
        {hasRole('ADMIN') && <Btn icon="plus" size="sm" onClick={() => setEdit('new')}>{t('addProduct')}</Btn>}
      </div>
      <div className="row between wrap" style={{ gap: 12, marginBottom: 18 }}>
        <div className="row gap-sm wrap">
          <span className="chip" data-on={cat === ''} onClick={() => setCat('')}>{t('allCats')}</span>
          {cats.data?.map((c) => (
            <span key={c.id} className="chip" data-on={cat === c.id} onClick={() => setCat(c.id)}>
              {lang === 'ru' ? c.nameRu : c.nameUz}
            </span>
          ))}
        </div>
      </div>
      <ErrorBox error={error} />
      <Card>
        <div className="table-wrap">
          <table className="tbl">
            <thead><tr>
              <th>{t('productName')}</th><th>SKU</th><th>{t('category')}</th><th className="num">{t('price')}</th>
              <th className="num">{t('defMarkup')}</th><th>{t('termRange')}</th><th className="num">{t('stock')}</th><th>{t('status')}</th>
            </tr></thead>
            <tbody>
              {data?.map((p) => (
                <tr key={p.id} onClick={() => hasRole('ADMIN') && setEdit(p)}>
                  <td className="cell-main"><b>{p.name}</b></td>
                  <td className="mono faint" data-label="SKU">{p.sku || '—'}</td>
                  <td className="muted" data-label={t('category')}>{catName(p.categoryId)}</td>
                  <td className="num" data-label={t('price')}>{fmtSom(p.price, lang)}</td>
                  <td className="num" data-label={t('defMarkup')}>{p.markupPct}%</td>
                  <td data-label={t('termRange')}>{p.termMin}–{p.termMax} {t('months')}</td>
                  <td className="num" data-label={t('stock')}>
                    <div className="row" style={{ gap: 6, justifyContent: 'flex-end' }}>
                      {p.active && p.stock <= 0 && <Badge tone="danger">{t('outOfStock')}</Badge>}
                      {p.active && p.stock > 0 && p.stock <= LOW_STOCK && <Badge tone="warn">{t('lowStock')}</Badge>}
                      <b>{p.stock}</b>
                    </div>
                  </td>
                  <td data-label={t('status')}>{p.active ? <Badge tone="success" dot>{t('active')}</Badge> : <Badge>{t('inactive')}</Badge>}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {data && data.length === 0 && <Empty />}
      </Card>
      {edit && <ProductModal product={edit === 'new' ? null : edit} categories={cats.data ?? []} onClose={() => setEdit(null)} />}
    </div>
  );
}

function ProductModal({ product, categories, onClose }: { product: Product | null; categories: Category[]; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [d, setD] = useState<Draft>(() => product
    ? { ...product }
    : { categoryId: categories[0]?.id ?? 0, name: '', price: 0, markupPct: 20, termMin: 3, termMax: 12, stock: 0, active: true });
  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setD((s) => ({ ...s, [k]: v }));
  const save = useMutation({
    mutationFn: () => (product ? api.put(`/api/products/${product.id}`, d) : api.post('/api/products', d)),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['products'] });
      onClose();
    },
  });
  const num = (k: 'price' | 'markupPct' | 'termMin' | 'termMax' | 'stock', label: string) => (
    <Field label={label}><input className="input" type="number" value={d[k]} onChange={(e) => set(k, Number(e.target.value))} /></Field>
  );
  return (
    <Modal title={product ? t('editProduct') : t('addProduct')} onClose={onClose}
      footer={<>
        <Btn variant="ghost" onClick={onClose}>{t('cancel')}</Btn>
        <Btn icon="check" disabled={save.isPending || !d.name} onClick={() => save.mutate()}>{t('save')}</Btn>
      </>}>
      <div className="col" style={{ gap: 14 }}>
        <ErrorBox error={save.error} />
        <Field label={t('productName')} req><input className="input" value={d.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="SKU"><input className="input mono" value={d.sku ?? ''} maxLength={50} onChange={(e) => set('sku', e.target.value)} /></Field>
        <Field label={t('category')}>
          <select className="select" value={d.categoryId} onChange={(e) => set('categoryId', Number(e.target.value))}>
            {categories.map((c) => <option key={c.id} value={c.id}>{i18n.language === 'ru' ? c.nameRu : c.nameUz}</option>)}
          </select>
        </Field>
        <div className="form-grid">
          {num('price', t('price'))}
          {num('markupPct', `${t('defMarkup')} %`)}
          {num('termMin', `${t('term')} (${t('from')})`)}
          {num('termMax', `${t('term')} (${t('to')})`)}
          {num('stock', t('stock'))}
        </div>
        <label className="check-row"><input type="checkbox" checked={d.active} onChange={(e) => set('active', e.target.checked)} />{t('active')}</label>
      </div>
    </Modal>
  );
}
