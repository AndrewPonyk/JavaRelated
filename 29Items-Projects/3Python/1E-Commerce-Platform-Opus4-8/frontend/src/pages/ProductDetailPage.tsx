import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { catalogApi, recommendationsApi } from "@/api/endpoints";
import { ApiRequestError } from "@/api/client";
import { ErrorBanner, Spinner } from "@/components/common/Spinner";
import { useCartMutations } from "@/hooks/useCart";
import { useAuthStore } from "@/store/auth";

export function ProductDetailPage() {
  const { slug = "" } = useParams();
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const { addItem } = useCartMutations();
  const [quantity, setQuantity] = useState(1);
  const [feedback, setFeedback] = useState<string | null>(null);

  const { data: product, isLoading, isError, refetch } = useQuery({
    queryKey: ["product", slug],
    queryFn: () => catalogApi.retrieve(slug),
  });

  // Record a "view" interaction to feed the recommender (best-effort).
  useEffect(() => {
    if (product && isAuthenticated) {
      recommendationsApi.track(product.id, "view").catch(() => undefined);
    }
  }, [product, isAuthenticated]);

  if (isLoading) return <Spinner />;
  if (isError || !product)
    return <ErrorBanner message="Product not found." onRetry={refetch} />;

  const handleAdd = async () => {
    if (!isAuthenticated) {
      navigate("/login", { state: { from: `/products/${slug}` } });
      return;
    }
    setFeedback(null);
    try {
      await addItem.mutateAsync({ productId: product.id, quantity });
      setFeedback("Added to cart ✓");
    } catch (err) {
      const message =
        err instanceof ApiRequestError ? err.message : "Could not add to cart.";
      setFeedback(message);
    }
  };

  return (
    <article className="mx-auto max-w-2xl rounded-lg bg-white p-6 shadow-sm">
      <p className="text-sm text-gray-400">{product.category.name}</p>
      <h1 className="mt-1 text-2xl font-bold text-gray-900">{product.name}</h1>
      <p className="mt-2 text-xl font-semibold text-brand">
        {product.currency} {product.price}
      </p>
      <p className="mt-4 whitespace-pre-line text-gray-700">{product.description}</p>
      <p className="mt-2 text-sm text-gray-500">
        {product.quantity_available > 0
          ? `${product.quantity_available} in stock`
          : "Out of stock"}
      </p>

      <div className="mt-6 flex items-center gap-3">
        <input
          type="number"
          min={1}
          max={Math.max(product.quantity_available, 1)}
          value={quantity}
          onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
          className="w-20 rounded-md border border-gray-300 px-3 py-2"
        />
        <button
          onClick={handleAdd}
          disabled={product.quantity_available <= 0 || addItem.isPending}
          className="rounded-md bg-brand px-5 py-2 text-white hover:bg-brand-dark disabled:opacity-50"
        >
          {addItem.isPending ? "Adding…" : "Add to cart"}
        </button>
      </div>
      {feedback && <p className="mt-3 text-sm text-gray-700">{feedback}</p>}
    </article>
  );
}
