/* Login sahifasi foni: zarrachalardan yig'iladigan 3D shakllar, to'lqin va kursor izi.
   React'dan tashqarida, to'g'ridan-to'g'ri canvas bilan ishlaydi (60fps uchun). */

type ShapeName = 'logo' | 'nn' | 'chart' | 'shield' | 'check';
interface Pt { x: number; y: number; z: number; r: number; g: number; b: number }
interface Particle {
  x: number; y: number; z: number; vx: number; vy: number; vz: number;
  r: number; g: number; b: number; tr: number; tg: number; tb: number;
  sx: number; sy: number; sz: number; ph: number; t0: number; a: number;
}

export const CYCLE: ShapeName[] = ['logo', 'nn', 'chart', 'shield'];
const N = 3200;
const SIZE = 360;

function rasterize(draw: (c: CanvasRenderingContext2D) => void): Pt[] {
  const o = document.createElement('canvas');
  o.width = o.height = SIZE;
  const c = o.getContext('2d')!;
  draw(c);
  const d = c.getImageData(0, 0, SIZE, SIZE).data;
  const a: Pt[] = [];
  for (let y = 0; y < SIZE; y += 3) {
    for (let q = 0; q < SIZE; q += 3) {
      const i = (y * SIZE + q) * 4;
      if (d[i + 3] > 140) {
        const gold = d[i] > 200 && d[i + 2] < 150;
        a.push({
          x: (q - 180) / 180, y: (y - 180) / 180,
          z: (Math.random() - 0.5) * 0.3 + (gold ? 0.22 : 0),
          r: d[i], g: d[i + 1], b: d[i + 2],
        });
      }
    }
  }
  for (let j = a.length - 1; j > 0; j--) {
    const k = Math.floor(Math.random() * (j + 1));
    [a[j], a[k]] = [a[k], a[j]];
  }
  return a;
}

const DRAW: Record<ShapeName, (c: CanvasRenderingContext2D) => void> = {
  logo(c) {
    c.setTransform(3, 0, 0, 3, 30, 51);
    c.lineJoin = 'round';
    c.lineWidth = 2.2;
    c.strokeStyle = '#5EEAD4';
    c.beginPath();
    if (c.roundRect) c.roundRect(0, 0, 100, 100, 28);
    else c.rect(0, 0, 100, 100);
    c.stroke();
    c.lineWidth = 5;
    const P = (p: string, col: string) => {
      c.fillStyle = c.strokeStyle = col;
      const path = new Path2D(p);
      c.fill(path);
      c.stroke(path);
    };
    P('M22 32H37L77 78H62Z', '#34D399');
    P('M22 32h15v46H22Z', '#FFFFFF');
    P('M62 16h15v62H62Z', '#34D399');
    P('M69.5-10L94 18H45Z', '#FCD34D');
  },
  nn(c) {
    c.fillStyle = '#CFFAF0';
    c.font = '800 330px Sora, Onest, sans-serif';
    c.textAlign = 'center';
    c.textBaseline = 'middle';
    c.fillText('N', 180, 200);
    c.fillStyle = '#FCD34D';
    c.beginPath();
    c.arc(280, 70, 16, 0, 6.3);
    c.fill();
  },
  chart(c) {
    c.fillStyle = '#34D399';
    for (const [x, y, w, h] of [[40, 230, 44, 90], [100, 190, 44, 130], [160, 150, 44, 170], [220, 105, 44, 215]]) {
      c.fillRect(x, y, w, h);
    }
    c.strokeStyle = '#FCD34D';
    c.lineWidth = 14;
    c.lineCap = c.lineJoin = 'round';
    c.beginPath();
    c.moveTo(40, 200);
    c.lineTo(120, 150);
    c.lineTo(185, 170);
    c.lineTo(300, 60);
    c.stroke();
    c.fillStyle = '#FCD34D';
    c.beginPath();
    c.moveTo(318, 40);
    c.lineTo(274, 48);
    c.lineTo(306, 86);
    c.fill();
  },
  shield(c) {
    c.setTransform(3, 0, 0, 3, 30, 30);
    c.fillStyle = '#22C58B';
    c.fill(new Path2D('M50 6L87 21V50C87 74 71 88 50 95C29 88 13 74 13 50V21Z'));
    c.fillStyle = '#fff';
    c.fillRect(31, 57, 10, 14);
    c.fillRect(45, 46, 10, 25);
    c.fillRect(59, 35, 10, 36);
    c.fillStyle = '#FCD34D';
    c.beginPath();
    c.arc(64, 26, 5, 0, 6.3);
    c.fill();
  },
  check(c) {
    c.lineCap = c.lineJoin = 'round';
    c.strokeStyle = '#FCD34D';
    c.lineWidth = 12;
    c.beginPath();
    c.arc(180, 180, 160, 0, 6.3);
    c.stroke();
    c.strokeStyle = '#5EEAD4';
    c.lineWidth = 40;
    c.beginPath();
    c.moveTo(94, 188);
    c.lineTo(156, 250);
    c.lineTo(268, 118);
    c.stroke();
  },
};

export interface SceneOptions {
  canvas: HTMLCanvasElement;
  /** Kursor bilan biroz og'adigan karta (faqat keng ekranda) */
  card?: HTMLElement | null;
  /** Shakl markazi o'zgarganda (caption joylashuvi uchun) */
  onLayout?: (x: number, y: number) => void;
  reducedMotion?: boolean;
}

export function createParticleScene(opts: SceneOptions) {
  const { canvas: cv, card, onLayout } = opts;
  const rm = !!opts.reducedMotion;
  const x = cv.getContext('2d')!;
  const shapes: Partial<Record<ShapeName, Pt[]>> = {};
  // Shakllar kerak bo'lganda yasaladi: "N" harfi chizilguncha Sora shrifti yuklanib ulguradi
  const shape = (n: ShapeName) => (shapes[n] ??= rasterize(DRAW[n]));

  let W = 0, H = 0, cx = 0, cy = 0, S = 1;
  let T = 0, mx = -999, my = -999, lm = -9, E = 0, fast = 0;
  let exploding = false, raf = 0, last = performance.now();
  const trail: { x: number; y: number }[] = [];
  const head = { x: 0, y: 0 };

  const PS: Particle[] = [];
  for (let i = 0; i < N; i++) {
    PS.push({
      x: (Math.random() - 0.5) * 7, y: (Math.random() - 0.5) * 5, z: (Math.random() - 0.5) * 4,
      vx: 0, vy: 0, vz: 0, r: 94, g: 234, b: 212, tr: 94, tg: 234, tb: 212,
      sx: 0, sy: 0, sz: 0, ph: Math.random() * 6.28, t0: 0.2 + Math.random() * 2.2, a: 0,
    });
  }

  function setShape(n: ShapeName) {
    const s = shape(n);
    PS.forEach((p, i) => {
      const q = s[i % s.length];
      p.sx = q.x; p.sy = q.y; p.sz = q.z; p.tr = q.r; p.tg = q.g; p.tb = q.b;
    });
  }

  function layout() {
    if (W < 900) {
      cx = W / 2; cy = H * 0.2; S = Math.min(W * 0.3, H * 0.14);
    } else {
      cx = W * 0.3; cy = H * 0.47; S = Math.min(W * 0.2, H * 0.3);
    }
    onLayout?.(cx, cy + S * 1.2);
  }

  function resize() {
    const d = Math.min(devicePixelRatio || 1, 1.6);
    W = innerWidth; H = innerHeight;
    cv.width = W * d; cv.height = H * d;
    x.setTransform(d, 0, 0, d, 0, 0);
    layout();
  }

  function onMove(e: PointerEvent) {
    mx = e.clientX; my = e.clientY; lm = T;
    if (card && !rm && innerWidth > 900) {
      card.style.transform = `rotateY(${-7 + (e.clientX / innerWidth - 0.5) * -5}deg) rotateX(${(e.clientY / innerHeight - 0.5) * -4}deg)`;
    }
  }

  function wave() {
    const NX = 72, NZ = 30;
    x.save();
    x.translate(W / 2, H / 2);
    x.rotate(-0.32);
    x.translate(-W / 2, -H / 2);
    for (let iz = 0; iz < NZ; iz++) {
      const d = iz / (NZ - 1), s = 0.35 + d * 1.15, z = 0.7 + d * 1.7;
      const a = (0.08 + 0.5 * d) * Math.min(T / 2, 1);
      for (let ix = 0; ix < NX; ix++) {
        const u = ix / (NX - 1) - 0.5;
        const w = Math.sin(ix * 0.22 + T * 1.1 + d * 4) + 0.55 * Math.sin(ix * 0.1 - T * 0.7 + d * 7);
        x.fillStyle = `rgba(${w > 0.9 ? '252,225,130' : '120,240,220'},${a})`;
        x.fillRect(W / 2 + u * W * 1.9 * s, H * (0.3 + 0.62 * d) - w * s * H * 0.045, z, z);
      }
    }
    x.restore();
  }

  function frame(now: number) {
    const dt = Math.min((now - last) / 1000, 0.05);
    last = now; T += dt; E *= 0.95; fast *= 0.98;

    x.clearRect(0, 0, W, H);
    x.globalCompositeOperation = 'lighter';
    wave();

    const ay = (mx > -900 ? (mx / W - 0.5) * 0.9 : 0) + Math.sin(T * 0.4) * 0.2;
    const ax = my > -900 ? (my / H - 0.5) * 0.4 : 0;
    const cy1 = Math.cos(ay), sy1 = Math.sin(ay), cx1 = Math.cos(ax), sx1 = Math.sin(ax);

    const gd = x.createRadialGradient(cx, cy, 0, cx, cy, S * 1.5);
    gd.addColorStop(0, `rgba(94,234,212,${0.16 * Math.min(T / 3, 1) * (exploding ? 0 : 1)})`);
    gd.addColorStop(1, 'rgba(94,234,212,0)');
    x.fillStyle = gd;
    x.fillRect(0, 0, W, H);

    for (const p of PS) {
      if (T < p.t0) continue;
      if (exploding) {
        p.vx *= 0.985; p.vy *= 0.985; p.vz *= 0.985; p.a -= 0.009;
      } else {
        const br = rm ? 0 : 0.012 + E * 0.03;
        const tx = p.sx + Math.sin(T * 1.2 + p.ph) * br;
        const ty = p.sy + Math.cos(T * 1.05 + p.ph) * br;
        const tz = p.sz + Math.sin(T * 0.9 + p.ph * 2) * br;
        const dx = tx - p.x, dy = ty - p.y;
        const sw = T < 4 && Math.abs(dx) + Math.abs(dy) > 0.2 ? 0.006 : 0;
        p.vx += dx * 0.05 - dy * sw; p.vy += dy * 0.05 + dx * sw; p.vz += (tz - p.z) * 0.05;
        p.vx *= 0.86; p.vy *= 0.86; p.vz *= 0.86;
        p.a += (1 - p.a) * 0.05;
        p.r += (p.tr - p.r) * 0.07; p.g += (p.tg - p.g) * 0.07; p.b += (p.tb - p.b) * 0.07;
      }
      p.x += p.vx; p.y += p.vy; p.z += p.vz;

      const X = p.x * cy1 + p.z * sy1, Z = -p.x * sy1 + p.z * cy1;
      const Y = p.y * cx1 - Z * sx1, Z2 = p.y * sx1 + Z * cx1;
      const ps = 1 / (1 - Z2 * 0.45), px = cx + X * S * ps, py = cy + Y * S * ps;

      if (!exploding) {
        const ox = px - mx, oy = py - my;
        let od = ox * ox + oy * oy;
        if (od < 15000 && od > 1) {
          od = Math.sqrt(od);
          let f = 1 - od / 122;
          f *= f * 0.05;
          p.vx += (ox / od) * f; p.vy += (oy / od) * f;
        }
      }
      const sz = (1.1 + ps * 0.9) * (1 + Math.min(Math.abs(p.vx) + Math.abs(p.vy), 0.1) * 8);
      const al = Math.max(p.a, 0) * (0.4 + 0.6 * Math.min(1, Math.max(0, Z2 + 0.6)));
      x.fillStyle = `rgba(${p.r | 0},${p.g | 0},${p.b | 0},${al * 0.9})`;
      x.fillRect(px, py, sz, sz);
    }

    // kursor (yoki avtomatik "kometa") izi
    const live = T - lm < 2.2;
    const tx2 = live ? mx : W * 0.55 + Math.sin(T * 0.7) * W * 0.3;
    const ty2 = live ? my : H * 0.58 + Math.sin(T * 1.3 + 1) * H * 0.2 + Math.cos(T * 0.5) * H * 0.08;
    head.x += (tx2 - head.x) * (0.07 + fast * 0.05);
    head.y += (ty2 - head.y) * (0.07 + fast * 0.05);
    trail.push({ x: head.x, y: head.y });
    if (trail.length > 95) trail.shift();

    x.lineCap = 'round';
    x.lineJoin = 'round';
    const L = trail.length;
    for (const [lw, alpha] of [[16, 0.1], [7, 0.22], [2.2, 0.95]]) {
      for (let j = 1; j < L; j++) {
        const q = j / L;
        const col = q > 0.75 ? '200,255,245' : q > 0.35 ? '94,234,212' : '139,92,246';
        x.strokeStyle = `rgba(${col},${alpha * Math.pow(q, 1.5) * Math.min(T / 3, 1)})`;
        x.lineWidth = lw * (0.25 + q * 0.75);
        x.beginPath();
        x.moveTo(trail[j - 1].x, trail[j - 1].y);
        x.lineTo(trail[j].x, trail[j].y);
        x.stroke();
      }
    }
    x.globalCompositeOperation = 'source-over';
    raf = requestAnimationFrame(frame);
  }

  resize();
  setShape('logo');
  head.x = W * 0.5; head.y = H * 0.6;
  addEventListener('resize', resize);
  addEventListener('pointermove', onMove);
  raf = requestAnimationFrame(frame);

  return {
    show(n: ShapeName) { exploding = false; setShape(n); E = 0.7; },
    /** Matn kiritilganda zarrachalar "jonlanadi" */
    poke() { E = 1; fast = 1; },
    /** Server javobini kutish */
    busy() { fast = 3; E = 2; },
    success() { setShape('check'); E = 0; },
    explode() {
      exploding = true;
      for (const p of PS) {
        const a = Math.random() * 6.283, s = 0.04 + Math.random() * 0.12;
        p.vx = Math.cos(a) * s; p.vy = Math.sin(a) * s; p.vz = (Math.random() - 0.5) * s;
      }
    },
    destroy() {
      cancelAnimationFrame(raf);
      removeEventListener('resize', resize);
      removeEventListener('pointermove', onMove);
    },
  };
}

export type ParticleScene = ReturnType<typeof createParticleScene>;
