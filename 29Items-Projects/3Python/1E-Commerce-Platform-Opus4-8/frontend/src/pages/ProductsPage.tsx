import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { catalogApi, recommendationsApi } from "@/api/endpoints";
import { ProductCard } from "@/components/product/ProductCard";
import { ErrorBanner, Spinner } from "@/components/common/Spinner";
import { useAuthStore } from "@/store/auth";

export function ProductsPage() {
  const [search, setSearch] = useState("");
  const [query, setQuery] = useState("");
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);

  const products = useQuery({
    queryKey: ["products", query],
    queryFn: () => catalogApi.list(query ? { search: query } : {}),
  });

  const recommendations = useQuery({
    queryKey: ["recommendations"],
    queryFn: recommendationsApi.forMe,
    enabled: isAuthenticated,
  });

  return (
    <div className="space-y-8">
      {isAuthenticated && recommendations.data && recommendations.data.results.length > 0 && (
        <section>
          <h2 className="mb-3 text-lg font-semibold text-gray-900">Recommended for you</h2>
          <ul className="grid grid-cols-2 gap-4 md:grid-cols-4">
            {recommendations.data.results.slice(0, 4).map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </ul>
        </section>
      )}

      <section>
        <form
          className="mb-4 flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            setQuery(search.trim());
          }}
        >
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search products…"
            className="flex-1 rounded-md border border-gray-300 px-3 py-2 focus:border-brand focus:outline-none"
          />
          <button
            type="submit"
            className="rounded-md bg-brand px-4 py-2 text-white hover:bg-brand-dark"
          >
            Search
          </button>
        </form>

        {products.isLoading && <Spinner />}
        {products.isError && (
          <ErrorBanner message="Failed to load products." onRetry={products.refetch} />
        )}
        {products.data && products.data.results.length === 0 && (
          <p className="text-gray-500">No products found.</p>
        )}
        {products.data && products.data.results.length > 0 && (
          <ul className="grid grid-cols-2 gap-4 md:grid-cols-4">
            {products.data.results.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
