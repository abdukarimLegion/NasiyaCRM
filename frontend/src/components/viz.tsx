import { useEffect, useId, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { Icon } from './ui';
import type { IconName } from './icons';

/* ------------------------------------------------------------------ o'lchov */

function useWidth<T extends HTMLElement>(): [React.RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [w, setW] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const ro = new ResizeObserver(([e]) => setW(Math.floor(e.contentRect.width)));
    ro.observe(el);
    setW(Math.floor(el.getBoundingClientRect().width));
    return () => ro.disconnect();
  }, []);
  return [ref, w];
}

/** 23 -> 25, 174 -> 200 (o'qdagi belgilar chiroyli chiqishi uchun) */
function nice(v: number): number {
  if (v <= 0) return 1;
  const pow = 10 ** Math.floor(Math.log10(v));
  const step = [1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find((s) => v <= s * pow) ?? 10;
  return step * pow;
}

/* ------------------------------------------------------------------ chiziqli / maydonli grafik */

export interface Series {
  key: string;
  label: string;
  color: string;
  type?: 'line' | 'area' | 'bar';
  dashed?: boolean;
}

/**
 * Bir nechta qatorli grafik: maydon (area), chiziq va ustunlar aralash bo'lishi mumkin.
 * Sichqoncha bilan oy ustiga kelinganda barcha qiymatlar ko'rsatiladi.
 */
export function LineChart({ data, series, height = 260, fmt = String, xKey = 'label' }: {
  data: Record<string, number | string>[];
  series: Series[];
  height?: number;
  fmt?: (n: number) => string;
  xKey?: string;
}) {
  const [ref, width] = useWidth<HTMLDivElement>();
  const [hover, setHover] = useState<number | null>(null);
  const uid = useId().replace(/:/g, '');
  const pad = { l: 48, r: 12, t: 14, b: 28 };
  const W = Math.max(width, 200);
  const H = height;
  const iw = W - pad.l - pad.r;
  const ih = H - pad.t - pad.b;
  const n = data.length;
  const values = data.flatMap((d) => series.map((s) => Number(d[s.key]) || 0));
  const rawMax = Math.max(0, ...values);
  const rawMin = Math.min(0, ...values);
  const max = nice(rawMax);
  const min = rawMin < 0 ? -nice(-rawMin) : 0;
  const y = (v: number) => pad.t + ih - ((v - min) / (max - min || 1)) * ih;
  const band = iw / Math.max(n, 1);
  const x = (i: number) => pad.l + band * i + band / 2;
  const ticks = [0, 0.25, 0.5, 0.75, 1].map((f) => min + (max - min) * f);
  const bars = series.filter((s) => s.type === 'bar');
  const barW = Math.min(26, (band * 0.7) / Math.max(bars.length, 1));

  const path = (key: string) => data.map((d, i) => `${i ? 'L' : 'M'}${x(i).toFixed(1)},${y(Number(d[key]) || 0).toFixed(1)}`).join('');

  const onMove = (e: React.MouseEvent<SVGSVGElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    const i = Math.floor((e.clientX - r.left - pad.l) / band);
    setHover(i >= 0 && i < n ? i : null);
  };

  return (
    <div className="lc" ref={ref} style={{ height: H }}>
      {width > 0 && (
        <svg width={W} height={H} onMouseMove={onMove} onMouseLeave={() => setHover(null)} role="img">
          <defs>
            {series.filter((s) => s.type === 'area').map((s) => (
              <linearGradient key={s.key} id={`g${uid}${s.key}`} x1="0" x2="0" y1="0" y2="1">
                <stop offset="0%" stopColor={s.color} stopOpacity="0.28" />
                <stop offset="100%" stopColor={s.color} stopOpacity="0.02" />
              </linearGradient>
            ))}
          </defs>
          {ticks.map((v) => (
            <g key={v}>
              <line x1={pad.l} x2={W - pad.r} y1={y(v)} y2={y(v)} className={v === 0 ? 'lc-zero' : 'lc-grid'} />
              <text x={pad.l - 8} y={y(v) + 4} className="lc-tick" textAnchor="end">{fmt(v)}</text>
            </g>
          ))}
          {hover != null && <rect x={pad.l + band * hover} y={pad.t} width={band} height={ih} className="lc-hover" />}
          {bars.map((s, bi) => data.map((d, i) => {
            const v = Number(d[s.key]) || 0;
            const bx = x(i) - (barW * bars.length) / 2 + bi * barW;
            return <rect key={`${s.key}${i}`} x={bx + 1} width={Math.max(barW - 2, 2)} y={Math.min(y(v), y(0))}
              height={Math.abs(y(v) - y(0))} rx={3} fill={s.color} opacity={hover == null || hover === i ? 0.9 : 0.45} />;
          }))}
          {series.filter((s) => s.type === 'area').map((s) => (
            <path key={`a${s.key}`} d={`${path(s.key)}L${x(n - 1)},${y(Math.max(min, 0))}L${x(0)},${y(Math.max(min, 0))}Z`}
              fill={`url(#g${uid}${s.key})`} />
          ))}
          {series.filter((s) => s.type !== 'bar').map((s) => (
            <path key={`l${s.key}`} d={path(s.key)} fill="none" stroke={s.color} strokeWidth={2.2}
              strokeDasharray={s.dashed ? '5 5' : undefined} strokeLinejoin="round" strokeLinecap="round" />
          ))}
          {hover != null && series.filter((s) => s.type !== 'bar').map((s) => (
            <circle key={`c${s.key}`} cx={x(hover)} cy={y(Number(data[hover][s.key]) || 0)} r={4.5}
              fill="var(--surface)" stroke={s.color} strokeWidth={2.2} />
          ))}
          {data.map((d, i) => (n <= 12 || i % Math.ceil(n / 12) === 0) && (
            <text key={i} x={x(i)} y={H - 8} className="lc-tick" textAnchor="middle">{String(d[xKey])}</text>
          ))}
        </svg>
      )}
      {hover != null && width > 0 && (
        <div className="lc-tip" style={{ left: Math.min(Math.max(x(hover) - 90, 4), W - 184) }}>
          <b>{String(data[hover][xKey])}</b>
          {series.map((s) => (
            <span key={s.key}><i style={{ background: s.color }} />{s.label}<em>{fmt(Number(data[hover][s.key]) || 0)}</em></span>
          ))}
        </div>
      )}
    </div>
  );
}

export function Legend({ series }: { series: Pick<Series, 'label' | 'color' | 'dashed'>[] }) {
  return (
    <div className="row" style={{ gap: 14, flexWrap: 'wrap' }}>
      {series.map((s) => (
        <span key={s.label} className="legend">
          <i style={{ background: s.dashed ? 'transparent' : s.color, border: s.dashed ? `2px dashed ${s.color}` : undefined }} />
          {s.label}
        </span>
      ))}
    </div>
  );
}

/* ------------------------------------------------------------------ halqa */

/** Bajarilish halqasi: 100% dan oshsa ham to'liq halqa + ichida haqiqiy foiz. */
export function Ring({ value, size = 64, stroke = 7, color = 'var(--primary)', children }: {
  value: number | null | undefined; size?: number; stroke?: number; color?: string; children?: ReactNode;
}) {
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = Math.max(0, Math.min(1, (value ?? 0) / 100));
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--surface-3)" strokeWidth={stroke} />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={color} strokeWidth={stroke}
          strokeDasharray={`${c * pct} ${c}`} strokeLinecap="round" style={{ transition: 'stroke-dasharray .7s' }} />
      </svg>
      <div className="ring-c">{children}</div>
    </div>
  );
}

/* ------------------------------------------------------------------ kichik chiziq */

export function Sparkline({ values, width = 120, height = 34, color = 'var(--primary)' }: {
  values: (number | null | undefined)[]; width?: number; height?: number; color?: string;
}) {
  const v = values.map((x) => x ?? 0);
  const max = Math.max(1, ...v);
  const step = v.length > 1 ? width / (v.length - 1) : width;
  const pts = v.map((x, i) => `${(i * step).toFixed(1)},${(height - 3 - (x / max) * (height - 6)).toFixed(1)}`);
  return (
    <svg width={width} height={height} className="spark">
      <polyline points={pts.join(' ')} fill="none" stroke={color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
      {pts.length > 0 && <circle cx={pts[pts.length - 1].split(',')[0]} cy={pts[pts.length - 1].split(',')[1]} r={3} fill={color} />}
    </svg>
  );
}

/* ------------------------------------------------------------------ tablar */

export interface TabItem {
  key: string;
  label: ReactNode;
  icon?: IconName;
  count?: number;
  to?: string;
  tone?: 'info' | 'warn' | 'danger' | 'success';
}

/** Sahifa ichidagi bo'limlar: `to` bo'lsa havola, aks holda tugma. */
export function Tabs({ items, value, onChange, style }: {
  items: TabItem[]; value?: string; onChange?: (k: string) => void; style?: CSSProperties;
}) {
  return (
    <div className="tabs" role="tablist" style={style}>
      {items.map((it) => {
        const inner = (
          <>
            {it.icon && <Icon name={it.icon} size={16} />}
            <span>{it.label}</span>
            {it.count != null && <span className={`tab-count${it.tone ? ` tc-${it.tone}` : ''}`}>{it.count}</span>}
          </>
        );
        return it.to ? (
          <NavLink key={it.key} to={it.to} end className="tab" role="tab">{inner}</NavLink>
        ) : (
          <button key={it.key} type="button" role="tab" className="tab" aria-selected={value === it.key}
            data-on={value === it.key} onClick={() => onChange?.(it.key)}>{inner}</button>
        );
      })}
    </div>
  );
}

/* ------------------------------------------------------------------ progress */

export function Progress({ pct, tone, height = 8 }: { pct: number | null | undefined; tone?: string; height?: number }) {
  const v = Math.max(0, Math.min(100, pct ?? 0));
  const color = tone ?? (v >= 100 ? 'var(--success)' : v >= 70 ? 'var(--primary)' : v >= 40 ? 'var(--gold)' : 'var(--danger)');
  return (
    <div className="prog" style={{ height }}>
      <i style={{ width: `${v}%`, background: color }} />
    </div>
  );
}
