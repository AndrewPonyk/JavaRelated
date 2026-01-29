/**
 * TypeScript types for E-Commerce Platform
 */

// User types
export interface User {
  id: string;
  email: string;
  first_name: string;
  last_name: string;
  full_name: string;
  phone_number?: string;
  avatar?: string;
  is_verified: boolean;
  is_vendor: boolean;
  created_at: string;
}

export interface Address {
  id: number;
  address_type: 'shipping' | 'billing';
  is_default: boolean;
  street_address: string;
  apartment?: string;
  city: string;
  state: string;
  postal_code: string;
  country: string;
}

// Product types
export interface Category {
  id: number;
  name: string;
  slug: string;
  description?: string;
  image?: string;
  parent?: number;
  children?: Category[];
  full_path: string;
}

export interface Product {
  id: string;
  sku: string;
  name: string;
  slug: string;
  category: Category;
  vendor_name: string;
  short_description: string;
  description: string;
  price: number;
  compare_at_price?: number;
  discount_percentage: number;
  status: 'draft' | 'pending' | 'active' | 'inactive';
  is_featured: boolean;
  images: ProductImage[];
  variants: ProductVariant[];
  reviews: ProductReview[];
  created_at: string;
}

export interface ProductListItem {
  id: string;
  sku: string;
  name: string;
  slug: string;
  category_name: string;
  price: number;
  compare_at_price?: number;
  discount_percentage: number;
  primary_image?: string;
  avg_rating?: number;
  is_featured: boolean;
}

export interface ProductImage {
  id: number;
  image: string;
  alt_text?: string;
  is_primary: boolean;
  sort_order: number;
}

export interface ProductVariant {
  id: number;
  sku: string;
  name: string;
  price?: number;
  effective_price: number;
  attributes: Record<string, string>;
  is_active: boolean;
}

export interface ProductReview {
  id: number;
  user_name: string;
  rating: number;
  title: string;
  content: string;
  is_verified_purchase: boolean;
  created_at: string;
}

// Cart types
export interface Cart {
  id: string;
  items: CartItem[];
  item_count: number;
  subtotal: number;
  is_empty: boolean;
  created_at: string;
  updated_at: string;
}

export interface CartItem {
  id: number;
  product: ProductListItem;
  variant?: ProductVariant;
  quantity: number;
  unit_price: number;
  line_total: number;
  added_at: string;
}

// Order types
export interface Order {
  id: string;
  order_number: string;
  email: string;
  status: OrderStatus;
  payment_status: PaymentStatus;
  subtotal: number;
  tax_amount: number;
  shipping_amount: number;
  discount_amount: number;
  total: number;
  shipping_address: Record<string, string>;
  billing_address: Record<string, string>;
  customer_notes?: string;
  items: OrderItem[];
  created_at: string;
  paid_at?: string;
  shipped_at?: string;
  delivered_at?: string;
}

export interface OrderItem {
  id: number;
  product_name: string;
  product_sku: string;
  variant_name?: string;
  unit_price: number;
  quantity: number;
  line_total: number;
  fulfilled_quantity: number;
}

export type OrderStatus =
  | 'pending'
  | 'confirmed'
  | 'processing'
  | 'shipped'
  | 'delivered'
  | 'cancelled'
  | 'refunded';

export type PaymentStatus =
  | 'pending'
  | 'authorized'
  | 'captured'
  | 'failed'
  | 'refunded';

// Search types
export interface SearchResult {
  products: ProductListItem[];
  total: number;
  page: number;
  page_size: number;
  total_pages: number;
  facets: SearchFacets;
}

export interface SearchFacets {
  categories: { slug: string; count: number }[];
  vendors: { slug: string; count: number }[];
  price_ranges: { range: string; count: number }[];
}

// API Response types
export interface PaginatedResponse<T> {
  count: number;
  next: string | null;
  previous: string | null;
  results: T[];
}

export interface ApiError {
  error: {
    code: string;
    message: string;
    details?: Record<string, string[]>;
    request_id: string;
    timestamp: string;
  };
}

// Auth types
export interface LoginCredentials {
  email: string;
  password: string;
}

export interface RegisterData {
  email: string;
  password: string;
  password_confirm: string;
  first_name: string;
  last_name: string;
  phone_number?: string;
}

export interface AuthTokens {
  access: string;
  refresh: string;
}

// Wishlist types
export interface WishlistItem {
  id: number;
  product_id: string;
  product_name: string;
  product_slug: string;
  product_price: number;
  product_image?: string;
  created_at: string;
}
