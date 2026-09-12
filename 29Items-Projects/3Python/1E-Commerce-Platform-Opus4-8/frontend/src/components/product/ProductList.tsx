// Example component (Deliverable 4.1) demonstrating data fetching plus
// loading, error, empty, and success display states.
import { useProducts } from "@/hooks/useProducts";
import { ApiRequestError } from "@/api/client";
import { ErrorBanner } from "@/components/common/Spinner";
import { ProductCard } from "./ProductCard";

interface ProductListProps {
  categorySlug?: string;
}

export function ProductList({ categorySlug }: ProductListProps) {
  const { data, isLoading, isError, error, refetch } = useProducts(categorySlug);

  if (isLoading) {
    return (
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4" aria-busy="true">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="h-40 animate-pulse rounded-lg bg-gray-200" />
        ))}
      </div>
    );
  }

  if (isError) {
    const message =
      error instanceof ApiRequestError ? error.message : "Something went wrong.";
    return <ErrorBanner message={`Failed to load products: ${message}`} onRetry={refetch} />;
  }

  const products = data?.results ?? [];
  if (products.length === 0) {
    return <p className="text-gray-500">No products found.</p>;
  }

  return (
    <ul className="grid grid-cols-2 gap-4 md:grid-cols-4">
      {products.map((product) => (
        <ProductCard key={product.id} product={product} />
      ))}
    </ul>
  );
}
