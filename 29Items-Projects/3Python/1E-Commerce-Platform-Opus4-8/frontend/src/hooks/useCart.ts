// Cart data + mutations via TanStack Query. Mutations invalidate the cached
// cart so the UI stays consistent after add/update/remove.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { cartApi } from "@/api/endpoints";
import { useAuthStore } from "@/store/auth";

const CART_KEY = ["cart"];

export function useCart() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  return useQuery({
    queryKey: CART_KEY,
    queryFn: cartApi.get,
    enabled: isAuthenticated,
  });
}

export function useCartMutations() {
  const qc = useQueryClient();
  const invalidate = () => qc.invalidateQueries({ queryKey: CART_KEY });

  const addItem = useMutation({
    mutationFn: ({ productId, quantity }: { productId: number; quantity: number }) =>
      cartApi.addItem(productId, quantity),
    onSuccess: invalidate,
  });
  const updateItem = useMutation({
    mutationFn: ({ id, quantity }: { id: number; quantity: number }) =>
      cartApi.updateItem(id, quantity),
    onSuccess: invalidate,
  });
  const removeItem = useMutation({
    mutationFn: (id: number) => cartApi.removeItem(id),
    onSuccess: invalidate,
  });
  const clear = useMutation({ mutationFn: cartApi.clear, onSuccess: invalidate });

  return { addItem, updateItem, removeItem, clear };
}
