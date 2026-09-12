'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { ApiError, orderApi } from '@/lib/api';
import { useAuth } from '@/context/AuthContext';
import { money } from '@/lib/format';
import type { Order } from '@/types/catalog';

export default function OrdersPage() {
  const { username } = useAuth();
  const [orders, setOrders] = useState<Order[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!username) return;
    orderApi
      .listForCustomer(username)
      .then((page) => setOrders(page.content))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load orders.'));
  }, [username]);

  if (!username) {
    return (
      <section>
        <h2>Your orders</h2>
        <p>
          Please <Link href="/login">log in</Link> to see your orders.
        </p>
      </section>
    );
  }

  return (
    <section>
      <h2>Your orders</h2>
      {error && <p className="error">{error}</p>}
      {orders === null ? (
        <p role="status">Loading…</p>
      ) : orders.length === 0 ? (
        <p>No orders yet.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Order</th>
              <th>Date</th>
              <th>Status</th>
              <th>Total</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => (
              <tr key={o.id}>
                <td>
                  <Link href={`/orders/${o.id}`}>{o.id.slice(0, 8)}…</Link>
                </td>
                <td>{new Date(o.createdAt).toLocaleString()}</td>
                <td>
                  <span className="status-pill">{o.status}</span>
                </td>
                <td>{money(o.totalAmount, o.currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
