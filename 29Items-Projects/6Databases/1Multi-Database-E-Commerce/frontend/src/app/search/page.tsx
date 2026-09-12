'use client';

import { useState } from 'react';
import ProductCard from '@/components/ProductCard';
import { ApiError, searchApi } from '@/lib/api';
import type { Product } from '@/types/catalog';

export default function SearchPage() {
  const [q, setQ] = useState('');
  const [results, setResults] = useState<Product[] | null>(null);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const res = await searchApi.search({ q, size: 24 });
      setResults(res.items);
      setTotal(res.total);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Search failed.');
      setResults([]);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section>
      <h2>Search</h2>
      <form onSubmit={run} style={{ display: 'flex', gap: '0.5rem', maxWidth: 520 }}>
        <input
          placeholder="Search products…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          aria-label="Search query"
        />
        <button type="submit" className="btn" disabled={loading}>
          {loading ? '…' : 'Search'}
        </button>
      </form>

      {error && <p className="error">{error}</p>}
      {results !== null && !error && (
        <>
          <p className="muted">{total} result(s)</p>
          {results.length === 0 ? (
            <p>No matches.</p>
          ) : (
            <div className="grid">
              {results.map((p) => (
                <ProductCard key={p.id} product={p} />
              ))}
            </div>
          )}
        </>
      )}
    </section>
  );
}
