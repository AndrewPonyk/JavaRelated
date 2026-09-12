'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { authApi, setToken } from '@/lib/api';

interface AuthState {
  username: string | null;
  login: (username: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);
const USER_KEY = 'shopflow.username';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [username, setUsername] = useState<string | null>(null);

  useEffect(() => {
    setUsername(window.localStorage.getItem(USER_KEY));
  }, []);

  const login = useCallback(async (user: string, password: string) => {
    const tokens = await authApi.login(user, password);
    setToken(tokens.accessToken);
    window.localStorage.setItem(USER_KEY, user);
    setUsername(user);
  }, []);

  const register = useCallback(
    async (user: string, email: string, password: string) => {
      await authApi.register(user, email, password);
      await login(user, password);
    },
    [login],
  );

  const logout = useCallback(() => {
    setToken(null);
    window.localStorage.removeItem(USER_KEY);
    setUsername(null);
  }, []);

  const value = useMemo(() => ({ username, login, register, logout }), [username, login, register, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
