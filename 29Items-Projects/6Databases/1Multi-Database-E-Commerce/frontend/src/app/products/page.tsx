import ProductList from '@/components/ProductList';

interface PageProps {
  searchParams: { category?: string };
}

/**
 * Products route. Server component that reads the optional `?category=` filter
 * and delegates rendering + data fetching to the client `ProductList`.
 */
export default function ProductsPage({ searchParams }: PageProps) {
  return (
    <section>
      <h2>Products</h2>
      <ProductList category={searchParams.category} />
    </section>
  );
}
