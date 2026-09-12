import { Link, useNavigate } from "react-router-dom";
import { useCart, useCartMutations } from "@/hooks/useCart";
import { ErrorBanner, Spinner } from "@/components/common/Spinner";

export function CartPage() {
  const { data: cart, isLoading, isError, refetch } = useCart();
  const { updateItem, removeItem, clear } = useCartMutations();
  const navigate = useNavigate();

  if (isLoading) return <Spinner />;
  if (isError || !cart) return <ErrorBanner message="Could not load cart." onRetry={refetch} />;

  if (cart.items.length === 0) {
    return (
      <div className="rounded-lg bg-white p-8 text-center shadow-sm">
        <p className="text-gray-600">Your cart is empty.</p>
        <Link to="/" className="mt-3 inline-block text-brand hover:underline">
          Browse products
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-gray-900">Your cart</h1>
      <ul className="divide-y divide-gray-200 rounded-lg bg-white shadow-sm">
        {cart.items.map((item) => (
          <li key={item.id} className="flex items-center justify-between gap-4 p-4">
            <div className="flex-1">
              <p className="font-medium text-gray-900">{item.product_name}</p>
              <p className="text-sm text-gray-500">
                {cart.items.length > 0 && `${item.unit_price} each`}
              </p>
            </div>
            <input
              type="number"
              min={1}
              value={item.quantity}
              onChange={(e) =>
                updateItem.mutate({ id: item.id, quantity: Math.max(1, Number(e.target.value)) })
              }
              className="w-16 rounded-md border border-gray-300 px-2 py-1"
            />
            <span className="w-24 text-right font-semibold">{item.line_total}</span>
            <button
              onClick={() => removeItem.mutate(item.id)}
              className="text-sm text-red-500 hover:text-red-700"
            >
              Remove
            </button>
          </li>
        ))}
      </ul>

      <div className="flex items-center justify-between rounded-lg bg-white p-4 shadow-sm">
        <button onClick={() => clear.mutate()} className="text-sm text-gray-500 hover:text-gray-700">
          Clear cart
        </button>
        <div className="text-right">
          <p className="text-lg font-bold">Total: {cart.total}</p>
          <button
            onClick={() => navigate("/checkout")}
            className="mt-2 rounded-md bg-brand px-6 py-2 text-white hover:bg-brand-dark"
          >
            Checkout
          </button>
        </div>
      </div>
    </div>
  );
}
