'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import { ApiError, orderApi } from '@/lib/api';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';
import { money } from '@/lib/format';

export default function CheckoutPage() {
  const { items, total, clear } = useCart();
  const { username } = useAuth();
  const router = useRouter();
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // A stable idempotency key per checkout attempt (survives retries).
  const idempotencyKey = useMemo(() => crypto.randomUUID(), []);
  const currency = items[0]?.currency ?? 'EUR';

  if (!username) {
    return (
      <section>
        <h2>Checkout</h2>
        <p>
          Please <Link href="/login">log in</Link> to place your order.
        </p>
      </section>
    );
  }

  if (items.length === 0) {
    return (
      <section>
        <h2>Checkout</h2>
        <p>Your cart is empty. <Link href="/products">Browse products</Link>.</p>
      </section>
    );
  }

  const placeOrder = async () => {
    setPlacing(true);
    setError(null);
    try {
      const order = await orderApi.create(
        {
          customerId: username,
          currency,
          items: items.map((i) => ({ productId: i.productId, quantity: i.quantity, unitPrice: i.price })),
        },
        idempotencyKey,
      );
      clear();
      router.push(`/orders/${order.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to place order.');
      setPlacing(false);
    }
  };

  return (
    <section>
      <h2>Checkout</h2>
      <p className="muted">Ordering as {username}</p>
      <ul>
        {items.map((i) => (
          <li key={i.productId}>
            {i.quantity} × {i.name} — {money(i.price * i.quantity, i.currency)}
          </li>
        ))}
      </ul>
      <p>
        <strong>Total: {money(total, currency)}</strong>
      </p>
      {error && <p className="error">{error}</p>}
      <button type="button" className="btn" onClick={placeOrder} disabled={placing}>
        {placing ? 'Placing order…' : 'Place order'}
      </button>
    </section>
  );
}
