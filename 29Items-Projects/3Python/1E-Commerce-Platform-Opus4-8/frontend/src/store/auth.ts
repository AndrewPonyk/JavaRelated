// Auth state in Zustand. Tokens persist in localStorage (via the client's
// tokenStore); the user object is hydrated on load and after login.
import { create } from "zustand";
import { tokenStore } from "@/api/client";
import { authApi } from "@/api/endpoints";
import type { User } from "@/types";

interface AuthState {
  user: User | null;
  initializing: boolean;
  isAuthenticated: boolean;
  hydrate: () => Promise<void>;
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, password: string, role?: string) => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  initializing: true,
  isAuthenticated: Boolean(tokenStore.access),

  hydrate: async () => {
    if (!tokenStore.access) {
      set({ initializing: false, isAuthenticated: false, user: null });
      return;
    }
    try {
      const user = await authApi.me();
      set({ user, isAuthenticated: true, initializing: false });
    } catch {
      tokenStore.clear();
      set({ user: null, isAuthenticated: false, initializing: false });
    }
  },

  login: async (email, password) => {
    const data = await authApi.login({ email, password });
    tokenStore.set(data.access, data.refresh);
    set({ user: data.user, isAuthenticated: true });
  },

  register: async (email, password, role) => {
    await authApi.register({ email, password, role });
    const data = await authApi.login({ email, password });
    tokenStore.set(data.access, data.refresh);
    set({ user: data.user, isAuthenticated: true });
  },

  logout: async () => {
    const refresh = tokenStore.refresh;
    if (refresh) {
      try {
        await authApi.logout(refresh);
      } catch {
        /* best-effort blacklist */
      }
    }
    tokenStore.clear();
    set({ user: null, isAuthenticated: false });
  },
}));
