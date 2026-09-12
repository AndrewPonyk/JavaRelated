import { useEffect } from "react";
import { Route, Routes } from "react-router-dom";
import { Layout } from "@/components/common/Layout";
import { ProtectedRoute } from "@/components/common/ProtectedRoute";
import { ProductsPage } from "@/pages/ProductsPage";
import { ProductDetailPage } from "@/pages/ProductDetailPage";
import { CartPage } from "@/pages/CartPage";
import { CheckoutPage } from "@/pages/CheckoutPage";
import { OrdersPage } from "@/pages/OrdersPage";
import { LoginPage } from "@/pages/LoginPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { VendorDashboardPage } from "@/pages/VendorDashboardPage";
import { useAuthStore } from "@/store/auth";

export function App() {
  const hydrate = useAuthStore((s) => s.hydrate);

  useEffect(() => {
    void hydrate();
  }, [hydrate]);

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<ProductsPage />} />
        <Route path="products/:slug" element={<ProductDetailPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />

        {/* Authenticated-only routes */}
        <Route element={<ProtectedRoute />}>
          <Route path="cart" element={<CartPage />} />
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="orders" element={<OrdersPage />} />
          <Route path="vendor" element={<VendorDashboardPage />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}

function NotFound() {
  return (
    <div className="py-16 text-center text-gray-500">
      <p className="text-2xl font-bold">404</p>
      <p>Page not found.</p>
    </div>
  );
}
