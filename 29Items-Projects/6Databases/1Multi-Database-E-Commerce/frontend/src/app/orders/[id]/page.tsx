'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiError, orderApi } from '@/lib/api';
import { money } from '@/lib/format';
import type { Order } from '@/types/catalog';

export default function OrderDetailPage({ params }: { params: { id: string } }) {
  const { id } = params;
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () =>
    orderApi
      .get(id)
      .then(setOrder)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load order.'));

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const pay = async () => {
    setBusy(true);
    setError(null);
    try {
      const updated = await orderApi.pay(id);
      setOrder(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Payment failed.');
    } finally {
      setBusy(false);
    }
  };

  if (error) return <p className="error">{error}</p>;
  if (!order) return <p role="status">Loading…</p>;

  return (
    <section>
      <Link href="/orders">← All orders</Link>
      <h2>Order {order.id.slice(0, 8)}…</h2>
      <p>
        Status: <span className="status-pill">{order.status}</span>
      </p>
      <p className="muted">Placed {new Date(order.createdAt).toLocaleString()}</p>

      <table>
        <thead>
          <tr>
            <th>Product</th>
            <th>Qty</th>
            <th>Unit</th>
            <th>Line total</th>
          </tr>
        </thead>
        <tbody>
          {order.items.map((line) => (
            <tr key={line.productId}>
              <td>{line.productId}</td>
              <td>{line.quantity}</td>
              <td>{money(line.unitPrice, order.currency)}</td>
              <td>{money(line.lineTotal, order.currency)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <p>
        <strong>Total: {money(order.totalAmount, order.currency)}</strong>
      </p>

      {order.status === 'PENDING' && (
        <button type="button" className="btn" onClick={pay} disabled={busy}>
          {busy ? 'Processing…' : 'Pay now'}
        </button>
      )}
      {order.status === 'PAID' && <p className="ok">Payment received — your order is being prepared.</p>}
    </section>
  );
}
