import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { ordersApi } from "@/api/endpoints";
import { ApiRequestError } from "@/api/client";
import { useCart } from "@/hooks/useCart";
import { ErrorBanner, Spinner } from "@/components/common/Spinner";

export function CheckoutPage() {
  const { data: cart, isLoading } = useCart();
  const [token, setToken] = useState("tok_visa");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const qc = useQueryClient();

  if (isLoading) return <Spinner />;
  if (!cart || cart.items.length === 0) {
    return <ErrorBanner message="Your cart is empty." />;
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const order = await ordersApi.checkout(token);
      await qc.invalidateQueries({ queryKey: ["cart"] });
      navigate(`/orders?placed=${order.number}`);
    } catch (err) {
      setError(err instanceof ApiRequestError ? err.message : "Checkout failed.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-lg rounded-lg bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-bold text-gray-900">Checkout</h1>
      <div className="mt-4 rounded-md bg-gray-50 p-4">
        <p className="flex justify-between">
          <span>{cart.item_count} item(s)</span>
          <span className="font-semibold">{cart.total}</span>
        </p>
      </div>

      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <label className="block">
          <span className="text-sm font-medium text-gray-700">Payment token</span>
          <input
            value={token}
            onChange={(e) => setToken(e.target.value)}
            className="mt-1 w-full rounded-md border border-gray-300 px-3 py-2"
            required
          />
          <span className="mt-1 block text-xs text-gray-400">
            Demo gateway: use “tok_visa” to succeed or “tok_decline” to simulate a decline.
          </span>
        </label>

        {error && <ErrorBanner message={error} />}

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-brand py-2.5 text-white hover:bg-brand-dark disabled:opacity-50"
        >
          {submitting ? "Placing order…" : `Pay ${cart.total}`}
        </button>
      </form>
    </div>
  );
}
