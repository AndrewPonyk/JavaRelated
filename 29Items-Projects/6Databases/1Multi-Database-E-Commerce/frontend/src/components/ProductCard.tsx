'use client';

import Link from 'next/link';
import { useCart } from '@/context/CartContext';
import { money, stars } from '@/lib/format';
import type { Product } from '@/types/catalog';

/** A single product tile: link to detail, rating, price, and add-to-cart. */
export default function ProductCard({ product }: { product: Product }) {
  const { add } = useCart();
  const outOfStock = product.stockOnHand <= 0;

  return (
    <div className="card">
      <Link href={`/products/${product.id}`} className="card-title">
        {product.name}
      </Link>
      <p className="muted">{product.category}</p>
      {product.reviewCount > 0 && (
        <p className="rating" title={`${product.averageRating} / 5`}>
          {stars(product.averageRating)} <span className="muted">({product.reviewCount})</span>
        </p>
      )}
      <strong>{money(product.price, product.currency)}</strong>
      <button
        type="button"
        className="btn"
        disabled={outOfStock}
        onClick={() => add(product)}
      >
        {outOfStock ? 'Out of stock' : 'Add to cart'}
      </button>
    </div>
  );
}
