import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../lib/auth';
import { Btn, Field, Icon } from '../components/ui';
import { setLang } from '../i18n';

const FEATURES = ['loginFeat1', 'loginFeat2', 'loginFeat3'] as const;
const YEAR = new Date().getFullYear();

export default function LoginPage() {
  const { t, i18n } = useTranslation();
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPass, setShowPass] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(username, password);
    } catch {
      setError(t('loginError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login" data-theme="light">
      <aside className="login-aside">
        <div className="login-brand">
          <div className="brand-mark">ن</div>
          <div className="col" style={{ gap: 2 }}>
            <b>{t('appName')}</b>
            <span>{t('appTag')}</span>
          </div>
        </div>

        <div className="login-pitch">
          <h2>{t('loginPitch')}</h2>
          <ul className="login-feats">
            {FEATURES.map((k) => (
              <li key={k}>
                <i><Icon name="check" size={15} /></i>
                {t(k)}
              </li>
            ))}
          </ul>
        </div>

        <p className="login-foot">© {YEAR} {t('appName')}</p>
      </aside>

      <main className="login-main">
        <div className="login-lang seg">
          {(['uz', 'ru'] as const).map((l) => (
            <button key={l} type="button" data-on={i18n.language === l} onClick={() => setLang(l)}
              style={{ textTransform: 'uppercase', fontSize: 12 }}>{l}</button>
          ))}
        </div>

        <form className="login-card anim" onSubmit={submit}>
          <div className="brand-mark login-mark-sm">ن</div>
          <h1>{t('welcome')}</h1>
          <p className="login-sub">{t('loginSub')}</p>

          {error && <div className="login-err"><Icon name="alert" size={17} />{error}</div>}

          <Field label={t('username')}>
            <div className="input-wrap">
              <Icon name="user" size={18} />
              <input className="input" autoFocus autoComplete="username" value={username}
                data-err={error ? 'true' : undefined}
                onChange={(e) => setUsername(e.target.value)} />
            </div>
          </Field>

          <Field label={t('password')}>
            <div className="input-wrap">
              <Icon name="lock" size={18} />
              <input className="input" data-eye="true" type={showPass ? 'text' : 'password'}
                autoComplete="current-password" value={password}
                data-err={error ? 'true' : undefined}
                onChange={(e) => setPassword(e.target.value)} />
              <button type="button" className="input-eye" onClick={() => setShowPass((v) => !v)}
                aria-label={t('password')}>
                <Icon name={showPass ? 'eyeOff' : 'eye'} size={18} />
              </button>
            </div>
          </Field>

          <Btn type="submit" size="lg" disabled={busy || !username || !password}
            style={{ width: '100%', marginTop: 4 }}>
            {busy ? t('loading') : t('login')}
          </Btn>
        </form>
      </main>
    </div>
  );
}
