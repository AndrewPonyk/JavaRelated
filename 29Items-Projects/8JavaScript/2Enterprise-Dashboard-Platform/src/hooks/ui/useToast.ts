import { useCallback } from 'react';
import { create } from 'zustand';

// Toast types
export type ToastVariant = 'default' | 'destructive' | 'success' | 'warning' | 'info';

export interface Toast {
  id: string;
  title?: string;
  description?: string;
  variant?: ToastVariant;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}

interface ToastStore {
  toasts: Toast[];
  addToast: (toast: Omit<Toast, 'id'>) => void;
  removeToast: (id: string) => void;
  removeAllToasts: () => void;
}

// Toast store
const useToastStore = create<ToastStore>((set, get) => ({
  toasts: [],

  addToast: (toast) => {
    const id = Math.random().toString(36).substr(2, 9);
    const newToast: Toast = {
      ...toast,
      id,
      variant: toast.variant || 'default',
      duration: toast.duration || 5000,
    };

    set((state) => ({
      toasts: [...state.toasts, newToast],
    }));

    // Auto-remove toast after duration
    if (newToast.duration && newToast.duration > 0) {
      setTimeout(() => {
        get().removeToast(id);
      }, newToast.duration);
    }
  },

  removeToast: (id) => {
    set((state) => ({
      toasts: state.toasts.filter((toast) => toast.id !== id),
    }));
  },

  removeAllToasts: () => {
    set({ toasts: [] });
  },
}));

// Toast hook
export const useToast = () => {
  const { addToast, removeToast, removeAllToasts } = useToastStore();

  const toast = useCallback(
    (props: Omit<Toast, 'id'>) => {
      addToast(props);
    },
    [addToast]
  );

  return {
    toast,
    dismiss: removeToast,
    dismissAll: removeAllToasts,
  };
};

// Convenience methods
export const toast = {
  success: (title: string, description?: string, options?: Partial<Toast>) => {
    useToastStore.getState().addToast({
      title,
      description,
      variant: 'success',
      ...options,
    });
  },

  error: (title: string, description?: string, options?: Partial<Toast>) => {
    useToastStore.getState().addToast({
      title,
      description,
      variant: 'destructive',
      ...options,
    });
  },

  warning: (title: string, description?: string, options?: Partial<Toast>) => {
    useToastStore.getState().addToast({
      title,
      description,
      variant: 'warning',
      ...options,
    });
  },

  info: (title: string, description?: string, options?: Partial<Toast>) => {
    useToastStore.getState().addToast({
      title,
      description,
      variant: 'info',
      ...options,
    });
  },
};

// Hook to get toasts (for the Toaster component)
export const useToastList = () => {
  return useToastStore((state) => state.toasts);
};

export default useToast;