/**
 * Authentication hook with Zustand store
 */
import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { authService } from '../services/auth';
import { cartService } from '../services/cart';
import type { User, LoginCredentials, RegisterData } from '../types';

// |su:39 Zustand: lightweight state management (alternative to Redux)
// create() returns a hook; the store is a single function with state + actions
// Unlike Redux: no reducers, no action types, no dispatch - just direct mutations

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;

  // Actions
  login: (credentials: LoginCredentials) => Promise<void>;
  register: (data: RegisterData) => Promise<void>;
  logout: () => Promise<void>;
  fetchProfile: () => Promise<void>;
  clearError: () => void;
}

// |su:40 persist() middleware: saves state to localStorage, survives page refresh
// partialize: only persist user & isAuthenticated (not isLoading, error)
export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      isAuthenticated: false,
      isLoading: false,
      error: null,

      login: async (credentials) => {
        set({ isLoading: true, error: null });
        try {
          const { user } = await authService.login(credentials);
          set({ user, isAuthenticated: true, isLoading: false });

          // Merge guest cart after login
          await cartService.mergeCart();
        } catch (error: any) {
          set({
            error: error.response?.data?.detail || 'Login failed',
            isLoading: false,
          });
          throw error;
        }
      },

      register: async (data) => {
        set({ isLoading: true, error: null });
        try {
          await authService.register(data);
          set({ isLoading: false });
        } catch (error: any) {
          set({
            error: error.response?.data?.detail || 'Registration failed',
            isLoading: false,
          });
          throw error;
        }
      },

      logout: async () => {
        await authService.logout();
        set({ user: null, isAuthenticated: false });
      },

      fetchProfile: async () => {
        if (!get().isAuthenticated) return;

        set({ isLoading: true });
        try {
          const user = await authService.getProfile();
          set({ user, isLoading: false });
        } catch (error) {
          set({ isLoading: false });
        }
      },

      clearError: () => set({ error: null }),
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        user: state.user,
        isAuthenticated: state.isAuthenticated,
      }),
    }
  )
);
