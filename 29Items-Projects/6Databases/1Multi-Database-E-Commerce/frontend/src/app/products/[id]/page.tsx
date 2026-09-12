'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import ReviewSection from '@/components/ReviewSection';
import { ApiError, catalogApi, recommendationApi } from '@/lib/api';
import { useCart } from '@/context/CartContext';
import { money, stars } from '@/lib/format';
import type { Product, ProductNode } from '@/types/catalog';

export default function ProductDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const { add } = useCart();

  const [product, setProduct] = useState<Product | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [alsoBought, setAlsoBought] = useState<ProductNode[]>([]);
  const [added, setAdded] = useState(false);

  useEffect(() => {
    setError(null);
    catalogApi
      .getProduct(id)
      .then(setProduct)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load product.'));
    recommendationApi.alsoBought(id, 6).then(setAlsoBought).catch(() => setAlsoBought([]));
  }, [id]);

  if (error) return <p className="error">{error}</p>;
  if (!product) return <p role="status">Loading…</p>;

  return (
    <article>
      <Link href="/products">← Back to products</Link>
      <h2>{product.name}</h2>
      <p className="muted">{product.category}</p>
      {product.reviewCount > 0 && (
        <p className="rating">
          {stars(product.averageRating)} <span className="muted">({product.reviewCount} reviews)</span>
        </p>
      )}
      <p>{product.description}</p>
      <p>
        <strong style={{ fontSize: '1.25rem' }}>{money(product.price, product.currency)}</strong>{' '}
        {product.stockOnHand > 0 ? (
          <span className="muted">{product.stockOnHand} in stock</span>
        ) : (
          <span className="error">Out of stock</span>
        )}
      </p>
      <button
        type="button"
        className="btn"
        disabled={product.stockOnHand <= 0}
        onClick={() => {
          add(product);
          setAdded(true);
        }}
      >
        Add to cart
      </button>
      {added && (
        <span className="ok" style={{ marginLeft: '0.75rem' }}>
          Added! <Link href="/cart">View cart</Link>
        </span>
      )}

      {alsoBought.length > 0 && (
        <section style={{ marginTop: '1.5rem' }}>
          <h3>Customers also bought</h3>
          <ul className="nav-links">
            {alsoBought.map((p) => (
              <li key={p.id}>
                <Link href={`/products/${p.id}`}>{p.name ?? p.id}</Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      <hr style={{ margin: '1.5rem 0' }} />
      <ReviewSection productId={product.id} />
    </article>
  );
}
