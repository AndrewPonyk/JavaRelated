import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '@/api/client';
import { createProduct, listMyProducts, updateProductPrice } from '@/api/products';
import { formatPrice, type CatalogProduct } from '@/types/product';

/**
 * Seller catalogue management: create products and adjust prices. Writes go to
 * the Catalog write model; the public search read model updates asynchronously.
 */
export function ProductManager(): JSX.Element {
  const [products, setProducts] = useState<CatalogProduct[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [stock, setStock] = useState('0');
  const [submitting, setSubmitting] = useState(false);

  const refresh = useCallback(async (): Promise<void> => {
    try {
      const result = await listMyProducts();
      setProducts(result.items);
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : 'Failed to load products.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onCreate(event: FormEvent): Promise<void> {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createProduct({
        name,
        description,
        priceMinor: Math.round(Number.parseFloat(price || '0') * 100),
        currency: 'USD',
        stock: Number.parseInt(stock || '0', 10),
      });
      setName('');
      setDescription('');
      setPrice('');
      setStock('0');
      await refresh();
    } catch (err: unknown) {
      setError(err instanceof ApiError ? err.message : 'Failed to create product.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section>
      <h2>Your products</h2>

      <form onSubmit={(e) => void onCreate(e)} aria-label="Create product">
        <label htmlFor="p-name">Name</label>
        <input id="p-name" required value={name} onChange={(e) => setName(e.target.value)} />

        <label htmlFor="p-desc">Description</label>
        <input id="p-desc" value={description} onChange={(e) => setDescription(e.target.value)} />

        <label htmlFor="p-price">Price (USD)</label>
        <input
          id="p-price"
          type="number"
          min="0"
          step="0.01"
          required
          value={price}
          onChange={(e) => setPrice(e.target.value)}
        />

        <label htmlFor="p-stock">Stock</label>
        <input id="p-stock" type="number" min="0" value={stock} onChange={(e) => setStock(e.target.value)} />

        <button type="submit" disabled={submitting}>
          {submitting ? 'Saving…' : 'Add product'}
        </button>
      </form>

      {error !== null && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      {loading ? (
        <p role="status">Loading your products…</p>
      ) : products.length === 0 ? (
        <p>You have no products yet.</p>
      ) : (
        <ul className="product-list">
          {products.map((product) => (
            <ProductRow key={product.id} product={product} onUpdated={refresh} />
          ))}
        </ul>
      )}
    </section>
  );
}

function ProductRow({
  product,
  onUpdated,
}: {
  product: CatalogProduct;
  onUpdated: () => Promise<void> | void;
}): JSX.Element {
  const [price, setPrice] = useState((product.price.amountMinor / 100).toFixed(2));
  const [busy, setBusy] = useState(false);

  async function save(): Promise<void> {
    setBusy(true);
    try {
      await updateProductPrice(product.id, Math.round(Number.parseFloat(price) * 100));
      await onUpdated();
    } finally {
      setBusy(false);
    }
  }

  return (
    <li className="product-card">
      <span className="product-card__name">{product.name}</span>
      <span className="product-card__price">
        {formatPrice(product.price.amountMinor, product.price.currency)}
      </span>
      <label htmlFor={`price-${product.id}`} className="sr-only">
        New price for {product.name}
      </label>
      <input
        id={`price-${product.id}`}
        type="number"
        min="0"
        step="0.01"
        value={price}
        onChange={(e) => setPrice(e.target.value)}
      />
      <button type="button" onClick={() => void save()} disabled={busy}>
        {busy ? 'Updating…' : 'Update price'}
      </button>
    </li>
  );
}
