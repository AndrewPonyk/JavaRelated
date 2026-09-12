'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import ProductList from '@/components/ProductList';
import { recommendationApi } from '@/lib/api';
import type { ProductNode } from '@/types/catalog';

export default function HomePage() {
  const [trending, setTrending] = useState<ProductNode[]>([]);

  useEffect(() => {
    // Best-effort; the graph may be empty until orders exist.
    recommendationApi.trending(6).then(setTrending).catch(() => setTrending([]));
  }, []);

  return (
    <section>
      <h2>Welcome to ShopFlow</h2>
      <p className="muted">A polyglot-persistence storefront — catalog, search, orders, and graph recommendations.</p>

      {trending.length > 0 && (
        <div>
          <h3>Trending now</h3>
          <ul className="nav-links">
            {trending.map((p) => (
              <li key={p.id}>
                <Link href={`/products/${p.id}`}>{p.name ?? p.id}</Link>
              </li>
            ))}
          </ul>
        </div>
      )}

      <h3 style={{ marginTop: '1.5rem' }}>Featured products</h3>
      <ProductList />
    </section>
  );
}
