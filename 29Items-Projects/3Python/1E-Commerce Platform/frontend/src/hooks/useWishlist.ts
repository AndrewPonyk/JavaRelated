/**
 * Wishlist hook with React Query
 */
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { wishlistService } from '../services/wishlist';
import { isAuthenticated } from '../services/api';

export function useWishlist() {
  const queryClient = useQueryClient();

  const {
    data: wishlistItems = [],
    isLoading,
    error,
  } = useQuery({
    queryKey: ['wishlist'],
    queryFn: wishlistService.getWishlist,
    enabled: isAuthenticated(),
    staleTime: 5 * 60 * 1000, // 5 minutes
    retry: false, // Don't retry on error
  });

  const addMutation = useMutation({
    mutationFn: wishlistService.addToWishlist,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });

  const removeMutation = useMutation({
    mutationFn: wishlistService.removeByProductId,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['wishlist'] });
    },
  });

  const isInWishlist = (productId: string): boolean => {
    if (!wishlistItems || !Array.isArray(wishlistItems)) return false;
    return wishlistItems.some((item) => String(item.product_id) === String(productId));
  };

  const toggleWishlist = async (productId: string) => {
    if (!isAuthenticated()) {
      // Redirect to login or show message
      window.location.href = '/login';
      return;
    }

    if (isInWishlist(productId)) {
      await removeMutation.mutateAsync(productId);
    } else {
      await addMutation.mutateAsync(productId);
    }
  };

  return {
    wishlistItems,
    isLoading,
    error,
    isInWishlist,
    toggleWishlist,
    addToWishlist: addMutation.mutate,
    removeFromWishlist: removeMutation.mutate,
    isUpdating: addMutation.isPending || removeMutation.isPending,
  };
}
