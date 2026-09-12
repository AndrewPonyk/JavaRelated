'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import type { Product } from '@/types/catalog';

export interface CartItem {
  productId: string;
  name: string;
  price: number;
  currency: string;
  quantity: number;
}

interface CartState {
  items: CartItem[];
  count: number;
  total: number;
  add: (product: Product, quantity?: number) => void;
  setQuantity: (productId: string, quantity: number) => void;
  remove: (productId: string) => void;
  clear: () => void;
}

const CartContext = createContext<CartState | undefined>(undefined);
const CART_KEY = 'shopflow.cart';

export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>([]);

  useEffect(() => {
    const raw = window.localStorage.getItem(CART_KEY);
    if (raw) setItems(JSON.parse(raw) as CartItem[]);
  }, []);

  const persist = useCallback((next: CartItem[]) => {
    setItems(next);
    window.localStorage.setItem(CART_KEY, JSON.stringify(next));
  }, []);

  const add = useCallback(
    (product: Product, quantity = 1) => {
      const existing = items.find((i) => i.productId === product.id);
      const next = existing
        ? items.map((i) => (i.productId === product.id ? { ...i, quantity: i.quantity + quantity } : i))
        : [
            ...items,
            {
              productId: product.id,
              name: product.name,
              price: product.price,
              currency: product.currency,
              quantity,
            },
          ];
      persist(next);
    },
    [items, persist],
  );

  const setQuantity = useCallback(
    (productId: string, quantity: number) => {
      if (quantity <= 0) {
        persist(items.filter((i) => i.productId !== productId));
        return;
      }
      persist(items.map((i) => (i.productId === productId ? { ...i, quantity } : i)));
    },
    [items, persist],
  );

  const remove = useCallback(
    (productId: string) => persist(items.filter((i) => i.productId !== productId)),
    [items, persist],
  );

  const clear = useCallback(() => persist([]), [persist]);

  const value = useMemo<CartState>(() => {
    const count = items.reduce((n, i) => n + i.quantity, 0);
    const total = items.reduce((sum, i) => sum + i.price * i.quantity, 0);
    return { items, count, total, add, setQuantity, remove, clear };
  }, [items, add, setQuantity, remove, clear]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error('useCart must be used within a CartProvider');
  return ctx;
}
