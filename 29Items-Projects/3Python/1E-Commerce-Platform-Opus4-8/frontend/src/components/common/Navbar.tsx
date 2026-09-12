import { Link, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/auth";
import { useCart } from "@/hooks/useCart";

export function Navbar() {
  const { user, isAuthenticated, logout } = useAuthStore();
  const { data: cart } = useCart();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <header className="border-b border-gray-200 bg-white">
      <nav className="mx-auto flex max-w-6xl items-center justify-between p-4">
        <Link to="/" className="text-xl font-bold text-brand">
          ShopOpus
        </Link>
        <div className="flex items-center gap-4 text-sm">
          <Link to="/" className="hover:text-brand">
            Products
          </Link>
          {isAuthenticated ? (
            <>
              <Link to="/orders" className="hover:text-brand">
                Orders
              </Link>
              {(user?.role === "vendor" || user?.role === "staff") && (
                <Link to="/vendor" className="hover:text-brand">
                  Vendor
                </Link>
              )}
              <Link to="/cart" className="relative hover:text-brand" aria-label="Cart">
                Cart
                {cart && cart.item_count > 0 && (
                  <span className="ml-1 rounded-full bg-brand px-2 py-0.5 text-xs text-white">
                    {cart.item_count}
                  </span>
                )}
              </Link>
              <span className="text-gray-400">{user?.email}</span>
              <button onClick={handleLogout} className="text-gray-600 hover:text-brand">
                Logout
              </button>
            </>
          ) : (
            <>
              <Link to="/login" className="hover:text-brand">
                Login
              </Link>
              <Link
                to="/register"
                className="rounded bg-brand px-3 py-1.5 text-white hover:bg-brand-dark"
              >
                Sign up
              </Link>
            </>
          )}
        </div>
      </nav>
    </header>
  );
}
