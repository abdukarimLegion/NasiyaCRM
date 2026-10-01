const MONTHS_UZ = ['yan', 'fev', 'mar', 'apr', 'may', 'iyn', 'iyl', 'avg', 'sen', 'okt', 'noy', 'dek'];
const MONTHS_RU = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];

export function fmtNum(n: number | null | undefined): string {
  if (n == null || Number.isNaN(n)) return '—';
  return Math.round(n).toLocaleString('ru-RU').replace(/[\s,]/g, ' ');
}

export function fmtSom(n: number | null | undefined, lang = 'uz'): string {
  if (n == null) return '—';
  return `${fmtNum(n)} ${lang === 'ru' ? 'сум' : "so'm"}`;
}

/** 2 147 000 000 -> "2,1 mlrd" */
export function fmtMln(n: number, lang = 'uz'): string {
  const u = lang === 'ru' ? { b: 'млрд', m: 'млн', k: 'тыс' } : { b: 'mlrd', m: 'mln', k: 'ming' };
  if (Math.abs(n) >= 1e9) return `${(n / 1e9).toFixed(1).replace('.', ',')} ${u.b}`;
  if (Math.abs(n) >= 1e6) return `${(n / 1e6).toFixed(n % 1e6 === 0 ? 0 : 1).replace('.', ',')} ${u.m}`;
  if (Math.abs(n) >= 1e3) return `${Math.round(n / 1e3)} ${u.k}`;
  return String(n);
}

export function fmtDate(d: string | Date | null | undefined, lang = 'uz'): string {
  if (!d) return '—';
  const dt = typeof d === 'string' ? new Date(d.length === 10 ? `${d}T00:00:00` : d) : d;
  const m = (lang === 'ru' ? MONTHS_RU : MONTHS_UZ)[dt.getMonth()];
  return `${dt.getDate()} ${m} ${dt.getFullYear()}`;
}

export function todayIso(): string {
  const d = new Date();
  const p = (x: number) => String(x).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function initials(name: string): string {
  const p = name.trim().split(/\s+/);
  return ((p[0]?.[0] ?? '') + (p[1]?.[0] ?? '')).toUpperCase();
}

export function avatarColor(name: string): string {
  const hues = [159, 84, 235, 27, 300, 200, 55];
  let h = 0;
  for (const c of name) h += c.charCodeAt(0);
  return `oklch(0.62 0.12 ${hues[h % hues.length]})`;
}
