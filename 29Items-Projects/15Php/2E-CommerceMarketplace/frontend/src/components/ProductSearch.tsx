import { useState } from 'react';
import { useProductSearch } from '@/hooks/useProductSearch';
import { formatPrice, type Product } from '@/types/product';

/**
 * Product search (Deliverable 4.1).
 *
 * Demonstrates the data-fetching + display pattern with explicit handling of
 * loading, error and empty states. Search hits the Elasticsearch read model via
 * the backend; results lag the write model by the (sub-second) projection time.
 */
export function ProductSearch(): JSX.Element {
  const [term, setTerm] = useState('');
  const state = useProductSearch(term);

  return (
    <section className="product-search">
      <label htmlFor="q" className="sr-only">
        Search products
      </label>
      <input
        id="q"
        type="search"
        placeholder="Search products…"
        value={term}
        onChange={(e) => setTerm(e.target.value)}
        autoComplete="off"
      />

      {state.status === 'idle' && <p>Start typing to search the catalog.</p>}
      {state.status === 'loading' && <p role="status">Searching…</p>}
      {state.status === 'error' && (
        <p role="alert" className="error">
          {state.message}
        </p>
      )}
      {state.status === 'success' &&
        (state.items.length === 0 ? (
          <p>No products match “{term}”.</p>
        ) : (
          <>
            <p>{state.total} result(s)</p>
            <ul className="product-list">
              {state.items.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </ul>
          </>
        ))}
    </section>
  );
}

function ProductCard({ product }: { product: Product }): JSX.Element {
  return (
    <li className="product-card">
      <span className="product-card__name">{product.name}</span>
      <span className="product-card__price">{formatPrice(product.priceMinor, product.currency)}</span>
    </li>
  );
}
