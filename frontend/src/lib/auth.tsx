import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { api, getToken, setToken, setUnauthorizedHandler } from '../api/client';
import type { LoginResponse, Permission, Role, UserView } from '../api/types';

interface AuthState {
  user: UserView | null;
  ready: boolean;
  /** onVerified: parol to'g'ri chiqqach, panelga o'tishdan oldin bajariladi (masalan, animatsiya) */
  login: (username: string, password: string, onVerified?: () => Promise<void>) => Promise<void>;
  logout: () => void;
  hasRole: (...roles: Role[]) => boolean;
  /** Ruxsatlardan kamida bittasi bo'lsa true */
  can: (...perms: Permission[]) => boolean;
  /** Ruxsat va filial o'zgargan bo'lsa qayta yuklash */
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserView | null>(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => setUser(null));
    if (!getToken()) {
      setReady(true);
      return;
    }
    api.get<UserView>('/api/auth/me')
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setReady(true));
  }, []);

  const login = useCallback(async (username: string, password: string, onVerified?: () => Promise<void>) => {
    const res = await api.post<LoginResponse>('/api/auth/login', { username, password });
    if (onVerified) await onVerified();
    setToken(res.token);
    setUser(res.user);
  }, []);

  const refresh = useCallback(async () => {
    if (!getToken()) return;
    setUser(await api.get<UserView>('/api/auth/me'));
  }, []);

  const value = useMemo<AuthState>(() => ({
    user, ready, login, logout, refresh,
    hasRole: (...roles) => !!user && roles.includes(user.role),
    can: (...perms) => !!user && perms.some((p) => user.permissions?.includes(p)),
  }), [user, ready, login, logout, refresh]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth AuthProvider ichida ishlatilishi kerak');
  return ctx;
}
