// Minimal auth context: holds the console JWT and exposes login/logout.
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

import { login as apiLogin } from '../api/client';

interface AuthState {
  token: string | null;
  scopes: string[];
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const STORAGE_KEY = 'ics.token';
const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem(STORAGE_KEY));
  const [scopes, setScopes] = useState<string[]>([]);

  const login = useCallback(async (username: string, password: string) => {
    const res = await apiLogin(username, password);
    localStorage.setItem(STORAGE_KEY, res.access_token);
    setToken(res.access_token);
    setScopes(res.scopes);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem(STORAGE_KEY);
    setToken(null);
    setScopes([]);
  }, []);

  const value = useMemo(() => ({ token, scopes, login, logout }), [token, scopes, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
