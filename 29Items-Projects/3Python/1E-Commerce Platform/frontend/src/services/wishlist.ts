/**
 * Wishlist API service
 */
import api from './api';
import type { WishlistItem } from '../types';

export const wishlistService = {
  /**
   * Get user's wishlist
   */
  async getWishlist(): Promise<WishlistItem[]> {
    const response = await api.get<any>('/auth/wishlist/');
    // Handle both paginated and non-paginated responses
    if (response.data.results) {
      return response.data.results;
    }
    if (Array.isArray(response.data)) {
      return response.data;
    }
    return [];
  },

  /**
   * Add product to wishlist
   */
  async addToWishlist(productId: string): Promise<WishlistItem> {
    const response = await api.post<WishlistItem>('/auth/wishlist/', {
      product_id: productId,
    });
    return response.data;
  },

  /**
   * Remove item from wishlist by wishlist item ID
   */
  async removeFromWishlist(itemId: number): Promise<void> {
    await api.delete(`/auth/wishlist/${itemId}/`);
  },

  /**
   * Remove item from wishlist by product ID
   */
  async removeByProductId(productId: string): Promise<void> {
    await api.delete(`/auth/wishlist/remove_by_product/?product_id=${productId}`);
  },

  /**
   * Check if product is in wishlist
   */
  async isInWishlist(productId: string): Promise<boolean> {
    const response = await api.get<{ in_wishlist: boolean }>(
      `/auth/wishlist/check/?product_id=${productId}`
    );
    return response.data.in_wishlist;
  },
};
