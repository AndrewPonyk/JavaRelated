// Authentication context: JWT login, token persistence, and current user.
// Tokens live in sessionStorage (cleared on tab close); the api client reads the
// access token from there on every request.

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, ApiError } from "@/api/client";
import type { User } from "@/types";

interface AuthState {
  user: User | null;
  loading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

const ACCESS_KEY = "access_token";
const REFRESH_KEY = "refresh_token";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const loadUser = useCallback(async () => {
    if (!sessionStorage.getItem(ACCESS_KEY)) {
      setLoading(false);
      return;
    }
    try {
      setUser(await api.get<User>("/auth/me/"));
    } catch {
      sessionStorage.removeItem(ACCESS_KEY);
      sessionStorage.removeItem(REFRESH_KEY);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadUser();
  }, [loadUser]);

  const login = useCallback(
    async (username: string, password: string) => {
      const tokens = await api.post<{ access: string; refresh: string }>("/auth/login/", {
        username,
        password,
      });
      sessionStorage.setItem(ACCESS_KEY, tokens.access);
      sessionStorage.setItem(REFRESH_KEY, tokens.refresh);
      setUser(await api.get<User>("/auth/me/"));
    },
    [],
  );

  const logout = useCallback(() => {
    sessionStorage.removeItem(ACCESS_KEY);
    sessionStorage.removeItem(REFRESH_KEY);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({ user, loading, login, logout }),
    [user, loading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}

export function isReadOnly(user: User | null): boolean {
  return !!user && (user.role === "AUDITOR" || user.role === "MONITOR");
}

export { ApiError };
