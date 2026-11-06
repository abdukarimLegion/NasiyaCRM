import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../lib/auth';
import { setLang } from '../i18n';
import { CYCLE, createParticleScene, type ParticleScene } from './login/particleScene';
import './login/login.css';

const CAPTIONS = ['loginCap1', 'loginCap2', 'loginCap3', 'loginCap4'] as const;
const SLIDE_MS = 5200;
const wait = (ms: number) => new Promise<void>((r) => setTimeout(r, ms));
const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

function Logo() {
  return (
    <svg viewBox="-8 -16 116 118" aria-hidden="true">
      <rect width="100" height="100" rx="28" fill="#0B1F18" stroke="#1F6F54" strokeWidth="1.5" />
      <path d="M22 32H37L77 78H62Z" fill="#34D399" stroke="#34D399" strokeWidth="5" strokeLinejoin="round" />
      <rect x="22" y="32" width="15" height="46" fill="#FFF" stroke="#FFF" strokeWidth="5" strokeLinejoin="round" />
      <rect x="62" y="16" width="15" height="62" fill="#34D399" stroke="#34D399" strokeWidth="5" strokeLinejoin="round" />
      <path d="M69.5-10L94 18H45Z" fill="#FCD34D" stroke="#FCD34D" strokeWidth="5" strokeLinejoin="round" />
    </svg>
  );
}

export default function LoginPage() {
  const { t, i18n } = useTranslation();
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [slide, setSlide] = useState(0);
  const [swapping, setSwapping] = useState(false);
  const [passFocus, setPassFocus] = useState(false);

  const rootRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const cardRef = useRef<HTMLElement>(null);
  const scene = useRef<ParticleScene | null>(null);
  const slideRef = useRef(0);

  useEffect(() => {
    const s = createParticleScene({
      canvas: canvasRef.current!,
      card: cardRef.current,
      reducedMotion: reducedMotion(),
      onLayout: (x, y) => {
        rootRef.current?.style.setProperty('--cx', `${x}px`);
        rootRef.current?.style.setProperty('--cy', `${y}px`);
      },
    });
    scene.current = s;
    return () => {
      s.destroy();
      scene.current = null;
    };
  }, []);

  // Shakllar navbatma-navbat almashadi (parol yozilayotganda va kirishda to'xtaydi)
  useEffect(() => {
    if (reducedMotion() || passFocus || busy || done) return;
    let swap: ReturnType<typeof setTimeout> | undefined;
    const id = setInterval(() => {
      const next = (slideRef.current + 1) % CYCLE.length;
      slideRef.current = next;
      scene.current?.show(CYCLE[next]);
      setSwapping(true);
      swap = setTimeout(() => {
        setSlide(next);
        setSwapping(false);
      }, 500);
    }, SLIDE_MS);
    return () => {
      clearInterval(id);
      clearTimeout(swap);
      setSlide(slideRef.current); // almashish o'rtasida to'xtasa, yozuv shaklga mos qolsin
      setSwapping(false);
    };
  }, [passFocus, busy, done]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (busy) return;
    if (!username.trim() || !password) {
      setError(t('loginEmpty'));
      return;
    }
    setBusy(true);
    setError(null);
    scene.current?.busy();
    try {
      await login(username.trim(), password, async () => {
        setDone(true);
        scene.current?.success();
        if (reducedMotion()) return wait(400);
        await wait(1500);
        scene.current?.explode();
        await wait(700);
      });
    } catch {
      setError(t('loginError'));
      setDone(false);
      setBusy(false);
    }
  };

  const poke = () => scene.current?.poke();

  return (
    <div ref={rootRef} className={`nl${done ? ' nl-fin' : ''}`}>
      <div className="nl-beam" />
      <canvas ref={canvasRef} className="nl-canvas" aria-hidden="true" />
      <div className="nl-vignette" />

      <div className="nl-top">
        <Logo />
        {t('appName')}
      </div>

      <div className="nl-lang" role="group" aria-label="Til">
        {(['uz', 'ru'] as const).map((l) => (
          <button key={l} type="button" aria-pressed={i18n.language === l} onClick={() => setLang(l)}>{l}</button>
        ))}
      </div>

      <div className={`nl-cap${swapping ? ' nl-sw' : ''}`} aria-hidden="true">
        <b>{t(CAPTIONS[slide])}</b>
        <i>0{slide + 1} / 0{CAPTIONS.length}</i>
      </div>

      <div className="nl-wrap">
        <section className="nl-card" ref={cardRef}>
          <h1>{t('welcome')}</h1>
          <p className="nl-sub">{t('loginSub')}</p>

          <form onSubmit={submit} noValidate>
            <div className="nl-f">
              <label htmlFor="nl-u">{t('username')}</label>
              <div>
                <input id="nl-u" type="text" autoFocus autoComplete="username" autoCapitalize="none"
                  spellCheck={false} value={username} aria-invalid={!!error}
                  onChange={(e) => { setUsername(e.target.value); poke(); }} />
              </div>
            </div>

            <div className="nl-f">
              <label htmlFor="nl-p">{t('password')}</label>
              <div>
                <input id="nl-p" type={showPass ? 'text' : 'password'} autoComplete="current-password"
                  placeholder="••••••••" value={password} aria-invalid={!!error}
                  onFocus={() => setPassFocus(true)} onBlur={() => setPassFocus(false)}
                  onChange={(e) => { setPassword(e.target.value); poke(); }} />
                <button className="nl-eye" type="button" onClick={() => setShowPass((v) => !v)}
                  aria-label={showPass ? t('hidePassword') : t('showPassword')} aria-pressed={showPass}>
                  <svg viewBox="0 0 24 24">
                    <path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z" />
                    <circle cx="12" cy="12" r="3" />
                    {showPass && <path d="M3 3l18 18" />}
                  </svg>
                </button>
              </div>
            </div>

            <div className="nl-err" role="alert">{error}</div>

            <button className={`nl-btn${busy ? ' nl-busy' : ''}`} type="submit" aria-busy={busy}>
              <span>{busy ? t('loginChecking') : t('login')}</span>
              <svg viewBox="0 0 24 24"><path d="M5 12h14M13 6l6 6-6 6" /></svg>
            </button>
          </form>
        </section>
      </div>

      <div className="nl-end" aria-live="polite">
        {done && (
          <>
            <b>{t('welcome')}</b>
            <span>{t('loginOpening')}</span>
          </>
        )}
      </div>
    </div>
  );
}
