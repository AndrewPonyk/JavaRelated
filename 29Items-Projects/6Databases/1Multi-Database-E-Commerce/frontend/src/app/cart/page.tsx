'use client';

import Link from 'next/link';
import { useCart } from '@/context/CartContext';
import { money } from '@/lib/format';

export default function CartPage() {
  const { items, total, setQuantity, remove } = useCart();

  if (items.length === 0) {
    return (
      <section>
        <h2>Your cart</h2>
        <p>Your cart is empty. <Link href="/products">Browse products</Link>.</p>
      </section>
    );
  }

  const currency = items[0]?.currency ?? 'EUR';

  return (
    <section>
      <h2>Your cart</h2>
      <table>
        <thead>
          <tr>
            <th>Product</th>
            <th>Price</th>
            <th>Qty</th>
            <th>Subtotal</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {items.map((i) => (
            <tr key={i.productId}>
              <td>{i.name}</td>
              <td>{money(i.price, i.currency)}</td>
              <td>
                <input
                  type="number"
                  min={0}
                  value={i.quantity}
                  style={{ width: 70 }}
                  onChange={(e) => setQuantity(i.productId, Number(e.target.value))}
                />
              </td>
              <td>{money(i.price * i.quantity, i.currency)}</td>
              <td>
                <button type="button" className="linklike" onClick={() => remove(i.productId)}>
                  Remove
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <p style={{ marginTop: '1rem' }}>
        <strong>Total: {money(total, currency)}</strong>
      </p>
      <Link href="/checkout" className="btn" style={{ display: 'inline-block', textDecoration: 'none' }}>
        Proceed to checkout
      </Link>
    </section>
  );
}
