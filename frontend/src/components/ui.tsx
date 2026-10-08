import { useEffect, type ReactNode, type ButtonHTMLAttributes, type CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { ICONS, type IconName } from './icons';
import { avatarColor, initials } from '../lib/format';
import { ApiError } from '../api/client';
import type { ContractStatus, RiskCategory, ScheduleStatus } from '../api/types';

export function Icon({ name, size = 20, style }: { name: IconName; size?: number; style?: CSSProperties }) {
  const filled = name === 'star';
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" style={style} aria-hidden="true"
      fill={filled ? 'currentColor' : 'none'} stroke={filled ? 'none' : 'currentColor'}
      strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round">
      <path d={ICONS[name]} />
    </svg>
  );
}

export function Card({ children, className = '', style }: { children: ReactNode; className?: string; style?: CSSProperties }) {
  return <div className={`card ${className}`} style={style}>{children}</div>;
}

export function CardHead({ title, sub, right }: { title: ReactNode; sub?: ReactNode; right?: ReactNode }) {
  return (
    <div className="card-head">
      <div className="col" style={{ gap: 1 }}>
        <h3>{title}</h3>
        {sub && <span className="sub">{sub}</span>}
      </div>
      {right && <div className="row" style={{ marginLeft: 'auto', gap: 8 }}>{right}</div>}
    </div>
  );
}

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'ghost' | 'soft' | 'gold';
  size?: 'sm' | 'lg';
  icon?: IconName;
};

export function Btn({ children, variant = 'primary', size, icon, className = '', type = 'button', ...rest }: BtnProps) {
  return (
    <button type={type} className={`btn btn-${variant}${size ? ` btn-${size}` : ''} ${className}`} {...rest}>
      {icon && <Icon name={icon} size={size === 'sm' ? 16 : 18} />}
      {children}
    </button>
  );
}

export type Tone = 'success' | 'warn' | 'danger' | 'info' | 'neutral';

export function Badge({ children, tone = 'neutral', dot = false }: { children: ReactNode; tone?: Tone; dot?: boolean }) {
  return <span className={`badge badge-${tone}`}>{dot && <i className="dotc" />}{children}</span>;
}

export function Avatar({ name, size = 34 }: { name: string; size?: number }) {
  return (
    <span className="av" style={{ width: size, height: size, background: avatarColor(name), borderRadius: '50%',
      display: 'grid', placeItems: 'center', color: '#fff', fontWeight: 700, fontSize: size * 0.38, flex: 'none' }}>
      {initials(name)}
    </span>
  );
}

const RISK_LABEL: Record<RiskCategory, string> = { A: 'riskLow', B: 'riskMid', C: 'riskHigh', D: 'riskReject' };

export function RiskPill({ cat, showLabel = false }: { cat?: RiskCategory | null; showLabel?: boolean }) {
  const { t } = useTranslation();
  if (!cat) return <span className="faint">—</span>;
  return (
    <span className={`risk-pill risk-${cat}`}>
      <span className="rg">{cat}</span>{showLabel && t(RISK_LABEL[cat])}
    </span>
  );
}

export type StatTone = 'primary' | 'success' | 'gold' | 'danger' | 'info';

export function Stat({ icon, label, value, sub, tone = 'primary' }: {
  icon: IconName; label: string; value: ReactNode; sub?: ReactNode; tone?: StatTone;
}) {
  return (
    <div className="stat anim" data-tone={tone}>
      <span className="stat-ic"><Icon name={icon} /></span>
      <span className="stat-label">{label}</span>
      <div className="stat-val">{value}</div>
      {sub && <span className="stat-sub">{sub}</span>}
    </div>
  );
}

export function MiniStat({ icon, label, value, tone = 'primary' }: {
  icon: IconName; label: string; value: ReactNode; tone?: StatTone;
}) {
  return (
    <div className="ministat anim" data-tone={tone}>
      <span className="stat-ic sm"><Icon name={icon} size={18} /></span>
      <span className="ministat-label">{label}</span>
      <b className="ministat-val tnum">{value}</b>
    </div>
  );
}

export function Bar({ pct, tone }: { pct: number; tone?: string }) {
  return <div className="bar"><i style={{ width: `${Math.max(0, Math.min(100, pct))}%`, background: tone }} /></div>;
}

export function Gauge({ value, max = 100, cat, size = 180, label, sublabel }: {
  value: number; max?: number; cat: RiskCategory; size?: number; label?: string; sublabel?: string;
}) {
  const r = size * 0.41, c = size / 2, circ = 2 * Math.PI * r, START = 0.74, arc = circ * START;
  const pct = Math.max(0, Math.min(1, value / max));
  const color = `var(--risk-${cat.toLowerCase()})`;
  const rot = 90 + (1 - START) * 180;
  return (
    <div style={{ position: 'relative', width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: `rotate(${rot}deg)` }}>
        <circle cx={c} cy={c} r={r} fill="none" stroke="var(--surface-3)" strokeWidth={size * 0.075}
          strokeLinecap="round" strokeDasharray={`${arc} ${circ}`} />
        <circle cx={c} cy={c} r={r} fill="none" stroke={color} strokeWidth={size * 0.075} strokeLinecap="round"
          strokeDasharray={`${arc * pct} ${circ}`} style={{ transition: 'stroke-dasharray .7s, stroke .4s' }} />
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
        <div className="mono tnum" style={{ fontSize: size * 0.28, fontWeight: 700, lineHeight: 1, color, letterSpacing: '-.03em' }}>{value}</div>
        {label && <div className="faint" style={{ fontSize: size * 0.07, marginTop: 4 }}>{label}</div>}
        {sublabel && <div style={{ fontSize: size * 0.075, fontWeight: 600, color, marginTop: 2 }}>{sublabel}</div>}
      </div>
    </div>
  );
}

export function Donut({ data, size = 150, thickness = 22, center }: {
  data: { cat: RiskCategory; pct: number }[]; size?: number; thickness?: number; center?: ReactNode;
}) {
  const r = (size - thickness) / 2, c = size / 2, circ = 2 * Math.PI * r;
  let acc = 0;
  return (
    <div style={{ position: 'relative', width: size, height: size, flex: 'none' }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} style={{ transform: 'rotate(-90deg)' }}>
        <circle cx={c} cy={c} r={r} fill="none" stroke="var(--surface-3)" strokeWidth={thickness} />
        {data.map((d) => {
          const len = (d.pct / 100) * circ;
          const off = acc;
          acc += len;
          return <circle key={d.cat} cx={c} cy={c} r={r} fill="none" stroke={`var(--risk-${d.cat.toLowerCase()})`}
            strokeWidth={thickness} strokeDasharray={`${len} ${circ - len}`} strokeDashoffset={-off} strokeLinecap="butt" />;
        })}
      </svg>
      {center && <div className="donut-center">{center}</div>}
    </div>
  );
}

/** O'qdagi belgilar chiroyli chiqishi uchun maksimumni yaxlitlaydi: 23 -> 25, 174 -> 200 */
function niceMax(v: number): number {
  if (v <= 0) return 1;
  const pow = 10 ** Math.floor(Math.log10(v));
  const step = [1, 2, 2.5, 5, 10].find((s) => v <= s * pow) ?? 10;
  return step * pow;
}

export function BarsChart({ data, keys, height = 210, fmt = String }: {
  data: Record<string, number | string>[];
  keys: { key: string; label: string; color: string }[];
  height?: number;
  fmt?: (n: number) => string;
}) {
  const max = niceMax(Math.max(...data.flatMap((d) => keys.map((k) => Number(d[k.key]) || 0))));
  const ticks = [1, 0.75, 0.5, 0.25, 0];
  return (
    <div className="chart" style={{ '--chart-h': `${height}px` } as CSSProperties}>
      <div className="chart-y">
        {ticks.map((f) => <span key={f}>{fmt(max * f)}</span>)}
      </div>
      <div className="chart-plot">
        <div className="chart-grid">{ticks.map((f) => <i key={f} />)}</div>
        {data.map((d, i) => (
          <div className="chart-col" key={i}>
            <div className="chart-stack">
              {keys.map((k) => {
                const v = Number(d[k.key]) || 0;
                return (
                  <span key={k.key} className="chart-bar" title={`${k.label}: ${fmt(v)}`}
                    style={{ height: `${(v / max) * 100}%`, minHeight: v > 0 ? 4 : 0, background: k.color }}>
                    {v > 0 && <b className="chart-tip">{fmt(v)}</b>}
                  </span>
                );
              })}
            </div>
            <span className="chart-x">{String(d.m)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

export function Field({ label, req, hint, err, children }: {
  label?: ReactNode; req?: boolean; hint?: ReactNode; err?: string; children: ReactNode;
}) {
  return (
    <div className="field">
      {label && <label>{label}{req && <span className="req">*</span>}</label>}
      {children}
      {err ? <span className="field-err">{err}</span> : hint && <span className="field-hint">{hint}</span>}
    </div>
  );
}

export function Seg<T extends string>({ value, options, onChange }: {
  value: T; options: { value: T; label: ReactNode }[]; onChange: (v: T) => void;
}) {
  return (
    <div className="seg">
      {options.map((o) => (
        <button key={o.value} type="button" data-on={o.value === value} onClick={() => onChange(o.value)}>{o.label}</button>
      ))}
    </div>
  );
}

export function Modal({ title, onClose, children, footer, wide }: {
  title: ReactNode; onClose: () => void; children: ReactNode; footer?: ReactNode; wide?: boolean;
}) {
  useEffect(() => {
    const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, [onClose]);
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal anim" style={{ maxWidth: wide ? 720 : 520 }} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3 style={{ margin: 0, fontSize: 15, fontWeight: 700 }}>{title}</h3>
          <button className="iconbtn" style={{ width: 34, height: 34 }} onClick={onClose}><Icon name="x" size={17} /></button>
        </div>
        <div className="modal-body scroll">{children}</div>
        {footer && <div className="modal-foot">{footer}</div>}
      </div>
    </div>
  );
}

export function Loading() {
  const { t } = useTranslation();
  return <div className="page muted">{t('loading')}</div>;
}

export function ErrorBox({ error }: { error: unknown }) {
  const { t } = useTranslation();
  if (!error) return null;
  const msg = error instanceof ApiError ? error.message : error instanceof Error ? error.message : String(error);
  return (
    <div className="badge badge-danger" style={{ display: 'flex', padding: '10px 14px', borderRadius: 10, whiteSpace: 'normal', height: 'auto' }}>
      <Icon name="alert" size={16} /> <span><b>{t('error')}:</b> {msg}</span>
    </div>
  );
}

export function Empty({ children }: { children?: ReactNode }) {
  const { t } = useTranslation();
  return <div className="muted" style={{ padding: 28, textAlign: 'center' }}>{children ?? t('empty')}</div>;
}

const CONTRACT_TONE: Record<ContractStatus, Tone> = {
  ACTIVE: 'success', LATE: 'danger', CLOSED: 'neutral', CANCELLED: 'neutral', DRAFT: 'info',
};
const CONTRACT_KEY: Record<ContractStatus, string> = {
  ACTIVE: 'st_active', LATE: 'st_late', CLOSED: 'st_closed', CANCELLED: 'st_cancelled', DRAFT: 'st_draft',
};
const SCHEDULE_TONE: Record<ScheduleStatus, Tone> = { PENDING: 'warn', PARTIAL: 'info', PAID: 'success', LATE: 'danger' };
const SCHEDULE_KEY: Record<ScheduleStatus, string> = { PENDING: 'st_unpaid', PARTIAL: 'st_partial', PAID: 'st_paid', LATE: 'st_late' };

export function ContractStatusBadge({ status }: { status: ContractStatus }) {
  const { t } = useTranslation();
  return <Badge tone={CONTRACT_TONE[status]} dot={status !== 'CLOSED'}>{t(CONTRACT_KEY[status])}</Badge>;
}

export function ScheduleStatusBadge({ status }: { status: ScheduleStatus }) {
  const { t } = useTranslation();
  return <Badge tone={SCHEDULE_TONE[status]} dot>{t(SCHEDULE_KEY[status])}</Badge>;
}
