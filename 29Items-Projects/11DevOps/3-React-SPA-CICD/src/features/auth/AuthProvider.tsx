import {
  createContext,
  useCallback,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';

import * as authApi from './auth.api';
import type { Credentials, User } from './auth.api';
import { ApiError } from '@/api/httpClient';
import { setUserId, trackEvent } from '@/lib/analytics/analytics';
import { logger } from '@/lib/logger';

/** unknown = session restore in flight; screens must not redirect while unknown. */
export type AuthStatus = 'unknown' | 'authenticated' | 'anonymous';

export interface AuthContextValue {
  user: User | null;
  status: AuthStatus;
  login: (credentials: Credentials) => Promise<void>;
  logout: () => Promise<void>;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>('unknown');

  // Session restore on boot: /me succeeds when a session survives — httpClient
  // transparently attempts a silent refresh + replay on 401 first. A final 401 here
  // is the normal signed-out case, not an error.
  useEffect(() => {
    let cancelled = false;
    authApi
      .fetchCurrentUser()
      .then((currentUser) => {
        if (cancelled) return;
        setUser(currentUser);
        setStatus('authenticated');
        setUserId(currentUser.id);
      })
      .catch(() => {
        if (cancelled) return;
        setUser(null);
        setStatus('anonymous');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (credentials: Credentials) => {
    trackEvent('login_attempt', { method: 'password' });
    try {
      const loggedInUser = await authApi.login(credentials);
      setUser(loggedInUser);
      setStatus('authenticated');
      setUserId(loggedInUser.id);
      trackEvent('login_success', { method: 'password' });
    } catch (error) {
      // Analytics hygiene: send the stable error CODE, never a free-form message that
      // could one day echo user input (docs/ARCHITECTURE.md §2.5 — ids, never PII).
      trackEvent('login_failure', {
        method: 'password',
        reason: error instanceof ApiError ? error.code : 'UNKNOWN',
      });
      throw error; // the form owns user-facing messaging
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch (error) {
      logger.warn('Logout API call failed; clearing local session anyway', {
        error: String(error),
      });
    } finally {
      setUser(null);
      setStatus('anonymous');
      setUserId(null);
    }
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({ user, status, login, logout }),
    [user, status, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
