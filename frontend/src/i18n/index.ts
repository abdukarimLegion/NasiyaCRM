import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import uz from './uz.json';
import ru from './ru.json';

const saved = (() => {
  try {
    return localStorage.getItem('lang');
  } catch {
    return null;
  }
})();

void i18n.use(initReactI18next).init({
  resources: { uz: { translation: uz }, ru: { translation: ru } },
  lng: saved === 'ru' ? 'ru' : 'uz',
  fallbackLng: 'uz',
  interpolation: { escapeValue: false },
});

export function setLang(lang: 'uz' | 'ru') {
  void i18n.changeLanguage(lang);
  try {
    localStorage.setItem('lang', lang);
  } catch {
    /* private mode */
  }
  document.documentElement.lang = lang;
}

export default i18n;
