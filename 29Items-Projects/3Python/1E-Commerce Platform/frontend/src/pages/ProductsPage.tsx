/**
 * Products listing page
 */
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useProducts, useCategories } from '../hooks/useProducts';
import ProductCard from '../components/products/ProductCard';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { cn } from '../utils/cn';

export default function ProductsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const category = searchParams.get('category') || undefined;
  const search = searchParams.get('search') || undefined;
  const sort = searchParams.get('sort') || '-created_at';

  const { data, isLoading, error } = useProducts({ category, search, sort });
  const { data: categories } = useCategories();

  const handleSortChange = (newSort: string) => {
    searchParams.set('sort', newSort);
    setSearchParams(searchParams);
  };

  const handleCategoryChange = (slug: string | null) => {
    if (slug) {
      searchParams.set('category', slug);
    } else {
      searchParams.delete('category');
    }
    setSearchParams(searchParams);
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <div className="flex flex-col lg:flex-row gap-8">
        {/* Sidebar filters */}
        <aside className="w-full lg:w-64 flex-shrink-0">
          <div className="sticky top-24">
            <h2 className="font-semibold text-gray-900">Categories</h2>
            <ul className="mt-4 space-y-2">
              <li>
                <button
                  onClick={() => handleCategoryChange(null)}
                  className={cn(
                    'text-sm',
                    !category ? 'text-primary-600 font-medium' : 'text-gray-600 hover:text-gray-900'
                  )}
                >
                  All Products
                </button>
              </li>
              {categories?.map((cat) => (
                <li key={cat.id}>
                  <button
                    onClick={() => handleCategoryChange(cat.slug)}
                    className={cn(
                      'text-sm',
                      category === cat.slug
                        ? 'text-primary-600 font-medium'
                        : 'text-gray-600 hover:text-gray-900'
                    )}
                  >
                    {cat.name}
                  </button>
                </li>
              ))}
            </ul>

            {/* TODO: Add price range filter */}
            {/* TODO: Add rating filter */}
          </div>
        </aside>

        {/* Main content */}
        <main className="flex-1">
          {/* Header */}
          <div className="flex items-center justify-between border-b pb-4">
            <div>
              <h1 className="text-2xl font-bold text-gray-900">
                {search ? `Search results for "${search}"` : 'All Products'}
              </h1>
              {data && (
                <p className="mt-1 text-sm text-gray-500">
                  {data.count} products found
                </p>
              )}
            </div>
            <select
              value={sort}
              onChange={(e) => handleSortChange(e.target.value)}
              className="input w-auto"
            >
              <option value="-created_at">Newest</option>
              <option value="price">Price: Low to High</option>
              <option value="-price">Price: High to Low</option>
              <option value="name">Name: A-Z</option>
              <option value="-name">Name: Z-A</option>
            </select>
          </div>

          {/* Products grid */}
          {isLoading ? (
            <LoadingSpinner />
          ) : error ? (
            <div className="py-12 text-center text-red-500">
              Failed to load products. Please try again.
            </div>
          ) : data?.results.length === 0 ? (
            <div className="py-12 text-center text-gray-500">
              No products found.
            </div>
          ) : (
            <div className="mt-8 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {data?.results.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}

          {/* TODO: Add pagination */}
        </main>
      </div>
    </div>
  );
}
