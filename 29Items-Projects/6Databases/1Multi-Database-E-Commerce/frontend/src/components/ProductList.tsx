'use client';

/**
 * ProductList — fetches a page of products and renders them, handling the four
 * states every data-driven component must: loading, error, empty, and success.
 */
import { useCallback, useEffect, useState } from 'react';
import { ApiError, catalogApi } from '@/lib/api';
import type { Product } from '@/types/catalog';
import ProductCard from './ProductCard';

interface ProductListProps {
  category?: string;
}

type LoadState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'success'; products: Product[] };

export default function ProductList({ category }: ProductListProps) {
  const [state, setState] = useState<LoadState>({ status: 'loading' });

  const load = useCallback(
    async (signal?: AbortSignal) => {
      setState({ status: 'loading' });
      try {
        const page = await catalogApi.listProducts({ category, size: 24 });
        if (signal?.aborted) return;
        setState({ status: 'success', products: page.content });
      } catch (err) {
        if (signal?.aborted) return;
        const message = err instanceof ApiError ? err.message : 'Failed to load products.';
        setState({ status: 'error', message });
      }
    },
    [category],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  if (state.status === 'loading') return <p role="status">Loading products…</p>;
  if (state.status === 'error') {
    return (
      <div role="alert" className="error">
        <p>{state.message}</p>
        <button type="button" className="btn" onClick={() => void load()}>
          Retry
        </button>
      </div>
    );
  }
  if (state.products.length === 0) {
    return <p>No products found{category ? ` in “${category}”` : ''}.</p>;
  }

  return (
    <div className="grid">
      {state.products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </div>
  );
}
