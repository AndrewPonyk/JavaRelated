/**
 * Cart hook with React Query
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cartService } from '../services/cart';
import type { Cart } from '../types';

// |su:41 React Query: server state management (different from Zustand's client state)
// Handles caching, background refetching, loading/error states automatically
// Query Key: unique identifier for cached data - changing key = new query

const CART_QUERY_KEY = ['cart'];

export function useCart() {
  // |su:42 queryClient: access to the cache; used to read/write cached data
  const queryClient = useQueryClient();

  // |su:43 useQuery: for READ operations (GET requests)
  // - Caches result automatically
  // - Re-fetches when component mounts or window regains focus
  // - staleTime: how long before data is considered "stale" and needs refresh
  const {
    data: cart,
    isLoading,
    error,
  } = useQuery<Cart>({
    queryKey: CART_QUERY_KEY,
    queryFn: cartService.getCart,
    staleTime: 30 * 1000, // 30 seconds - cart is fresh for 30s, then may refetch
  });

  // |su:44 useMutation: for WRITE operations (POST, PUT, DELETE)
  // - Does NOT cache (every call hits server)
  // - onSuccess: callback after successful mutation
  // - setQueryData: instantly update cache (optimistic or from response)
  // - invalidateQueries: mark cache as stale, trigger background refetch
  const addItemMutation = useMutation({
    mutationFn: ({
      productId,
      quantity,
      variantId,
    }: {
      productId: string;
      quantity?: number;
      variantId?: number;
    }) => cartService.addItem(productId, quantity, variantId),
    onSuccess: (updatedCart) => {
      // |su:45 Two-step cache update:
      // 1. setQueryData: immediate UI update with response data
      // 2. invalidateQueries: background refetch ensures consistency
      queryClient.setQueryData(CART_QUERY_KEY, updatedCart);
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });

  const updateQuantityMutation = useMutation({
    mutationFn: ({ itemId, quantity }: { itemId: number; quantity: number }) =>
      cartService.updateItemQuantity(itemId, quantity),
    onSuccess: (updatedCart) => {
      queryClient.setQueryData(CART_QUERY_KEY, updatedCart);
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });

  const removeItemMutation = useMutation({
    mutationFn: (itemId: number) => cartService.removeItem(itemId),
    onSuccess: (updatedCart) => {
      queryClient.setQueryData(CART_QUERY_KEY, updatedCart);
      queryClient.invalidateQueries({ queryKey: CART_QUERY_KEY });
    },
  });

  const clearCartMutation = useMutation({
    mutationFn: cartService.clearCart,
    onSuccess: (updatedCart) => {
      queryClient.setQueryData(CART_QUERY_KEY, updatedCart);
    },
  });

  return {
    cart,
    isLoading,
    error,
    itemCount: cart?.item_count ?? 0,
    subtotal: cart?.subtotal ?? 0,
    isEmpty: cart?.is_empty ?? true,

    // Actions
    addItem: addItemMutation.mutateAsync,
    updateQuantity: updateQuantityMutation.mutateAsync,
    removeItem: removeItemMutation.mutateAsync,
    clearCart: clearCartMutation.mutateAsync,

    // Mutation states
    isAddingItem: addItemMutation.isPending,
    isUpdating: updateQuantityMutation.isPending,
    isRemoving: removeItemMutation.isPending,
    isClearing: clearCartMutation.isPending,
  };
}
