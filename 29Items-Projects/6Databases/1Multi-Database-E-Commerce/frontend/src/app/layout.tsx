import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import './globals.css';
import Nav from '@/components/Nav';
import { AuthProvider } from '@/context/AuthContext';
import { CartProvider } from '@/context/CartContext';

export const metadata: Metadata = {
  title: 'ShopFlow',
  description: 'Multi-database e-commerce storefront',
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <CartProvider>
            <Nav />
            <main className="container">{children}</main>
          </CartProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
