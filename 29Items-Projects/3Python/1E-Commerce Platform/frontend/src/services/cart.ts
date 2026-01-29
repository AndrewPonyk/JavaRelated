/**
 * Cart API service
 */
import api from './api';
import type { Cart } from '../types';

export const cartService = {
  /**
   * Get current cart
   */
  async getCart(): Promise<Cart> {
    const response = await api.get<Cart>('/cart/');
    return response.data;
  },

  /**
   * Add item to cart
   */
  async addItem(
    productId: string,
    quantity = 1,
    variantId?: number
  ): Promise<Cart> {
    const response = await api.post<Cart>('/cart/add/', {
      product_id: productId,
      quantity,
      variant_id: variantId,
    });
    return response.data;
  },

  /**
   * Update cart item quantity
   */
  async updateItemQuantity(itemId: number, quantity: number): Promise<Cart> {
    const response = await api.patch<Cart>(`/cart/items/${itemId}/`, {
      quantity,
    });
    return response.data;
  },

  /**
   * Remove item from cart
   */
  async removeItem(itemId: number): Promise<Cart> {
    const response = await api.delete<Cart>(`/cart/items/${itemId}/remove/`);
    return response.data;
  },

  /**
   * Clear all items from cart
   */
  async clearCart(): Promise<Cart> {
    const response = await api.post<Cart>('/cart/clear/');
    return response.data;
  },

  /**
   * Merge guest cart after login
   */
  async mergeCart(): Promise<Cart> {
    const response = await api.post<Cart>('/cart/merge/');
    return response.data;
  },
};
