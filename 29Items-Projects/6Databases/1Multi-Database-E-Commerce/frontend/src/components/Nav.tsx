'use client';

import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';

/** Top navigation with cart count and auth state. */
export default function Nav() {
  const { count } = useCart();
  const { username, logout } = useAuth();

  return (
    <nav className="nav">
      <Link href="/" className="brand">
        ShopFlow
      </Link>
      <div className="nav-links">
        <Link href="/products">Products</Link>
        <Link href="/search">Search</Link>
        <Link href="/cart">Cart ({count})</Link>
        {username ? (
          <>
            <Link href="/orders">Orders</Link>
            <span className="muted">Hi, {username}</span>
            <button type="button" className="linklike" onClick={logout}>
              Logout
            </button>
          </>
        ) : (
          <Link href="/login">Login</Link>
        )}
      </div>
    </nav>
  );
}
