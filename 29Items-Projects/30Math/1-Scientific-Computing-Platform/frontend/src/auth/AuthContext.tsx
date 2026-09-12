/**
 * Session state for the whole app. On mount, an existing refresh token is
 * exchanged for a fresh access token by simply calling /auth/me (the client's
 * 401→refresh path does the work).
 */

import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import * as authApi from "../api/auth";
import { hasSession } from "../api/tokens";
import type { UserRead } from "../types/api";

interface AuthState {
  user: UserRead | null;
  initializing: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserRead | null>(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      if (hasSession()) {
        try {
          const me = await authApi.fetchCurrentUser();
          if (!cancelled) setUser(me);
        } catch {
          /* refresh token expired/revoked — stay signed out */
        }
      }
      if (!cancelled) setInitializing(false);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    await authApi.login(email, password);
    setUser(await authApi.fetchCurrentUser());
  }, []);

  const register = useCallback(
    async (email: string, password: string) => {
      await authApi.register(email, password);
      await login(email, password);
    },
    [login],
  );

  const logout = useCallback(async () => {
    await authApi.logout();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, initializing, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (state === null) {
    throw new Error("useAuth must be used inside <AuthProvider>");
  }
  return state;
}
