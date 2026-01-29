/**
 * Cart hook with React Query
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { cartService } from '../services/cart';
import type { Cart } from '../types';

const CART_QUERY_KEY = ['cart'];

export function useCart() {
  const queryClient = useQueryClient();

  const {
    data: cart,
    isLoading,
    error,
  } = useQuery<Cart>({
    queryKey: CART_QUERY_KEY,
    queryFn: cartService.getCart,
    staleTime: 30 * 1000, // 30 seconds
  });

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
