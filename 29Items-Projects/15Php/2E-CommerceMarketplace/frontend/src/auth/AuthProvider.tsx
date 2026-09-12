import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import * as authApi from '@/api/auth';
import type { AccountType, AuthUser } from '@/types/auth';
import { AuthContext, type AuthContextValue, TOKEN_STORAGE_KEY } from './AuthContext';

/**
 * Holds auth state (the decoded user) and persists the JWT in localStorage. On
 * mount it restores the session by calling /auth/me if a token is present.
 */
export function AuthProvider({ children }: { children: ReactNode }): JSX.Element {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (localStorage.getItem(TOKEN_STORAGE_KEY) === null) {
      setLoading(false);
      return;
    }
    authApi
      .fetchMe()
      .then(setUser)
      .catch(() => localStorage.removeItem(TOKEN_STORAGE_KEY))
      .finally(() => setLoading(false));
  }, []);

  const login = useCallback(async (email: string, password: string): Promise<void> => {
    const { token } = await authApi.login(email, password);
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    setUser(await authApi.fetchMe());
  }, []);

  const register = useCallback(
    async (email: string, password: string, accountType: AccountType): Promise<void> => {
      await authApi.register({ email, password, accountType });
      await login(email, password);
    },
    [login],
  );

  const logout = useCallback((): void => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setUser(null);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isSeller: user?.roles.includes('ROLE_SELLER') ?? false,
      loading,
      login,
      register,
      logout,
    }),
    [user, loading, login, register, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
