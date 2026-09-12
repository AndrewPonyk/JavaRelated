/** Shared domain types mirroring the backend API contract. */

export interface Money {
  amountMinor: number;
  currency: string;
}

export interface Product {
  id: string;
  sellerId: string;
  name: string;
  priceMinor: number;
  currency: string;
  createdAt?: string;
}

export interface ProductSearchResult {
  total: number;
  items: Product[];
}

/** Full product shape returned by the Catalog write-side API (seller views). */
export interface CatalogProduct {
  id: string;
  sellerId: string;
  name: string;
  description: string;
  price: Money;
  stock: number;
  active: boolean;
}

/** Format minor units (e.g. cents) as a localized currency string. */
export function formatPrice(amountMinor: number, currency: string): string {
  return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(
    amountMinor / 100,
  );
}
