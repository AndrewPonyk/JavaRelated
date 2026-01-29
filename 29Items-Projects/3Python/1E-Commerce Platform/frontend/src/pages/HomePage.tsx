/**
 * Home page component
 */
import { Link } from 'react-router-dom';
import { useFeaturedProducts, useCategories } from '../hooks/useProducts';
import ProductCard from '../components/products/ProductCard';
import LoadingSpinner from '../components/common/LoadingSpinner';

export default function HomePage() {
  const { data: featuredProducts, isLoading: loadingProducts } = useFeaturedProducts(8);
  const { data: categories, isLoading: loadingCategories } = useCategories();

  return (
    <div>
      {/* Hero Section */}
      <section className="bg-gradient-to-r from-primary-600 to-primary-800 text-white">
        <div className="mx-auto max-w-7xl px-4 py-24 sm:px-6 lg:px-8">
          <div className="text-center">
            <h1 className="text-4xl font-bold tracking-tight sm:text-5xl md:text-6xl">
              Discover Amazing Products
            </h1>
            <p className="mx-auto mt-6 max-w-2xl text-xl text-primary-100">
              Shop the latest trends with exclusive deals and fast shipping.
            </p>
            <div className="mt-10 flex justify-center gap-4">
              <Link to="/products" className="btn bg-white text-primary-600 hover:bg-gray-100 btn-lg">
                Shop Now
              </Link>
              <Link to="/products?featured=true" className="btn border-2 border-white text-white hover:bg-white/10 btn-lg">
                View Featured
              </Link>
            </div>
          </div>
        </div>
      </section>

      {/* Categories Section */}
      <section className="py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <h2 className="text-2xl font-bold text-gray-900">Shop by Category</h2>
          {loadingCategories ? (
            <LoadingSpinner />
          ) : (
            <div className="mt-8 grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-6">
              {categories?.slice(0, 6).map((category) => (
                <Link
                  key={category.id}
                  to={`/products?category=${category.slug}`}
                  className="group rounded-lg border p-4 text-center hover:border-primary-500 hover:shadow-md transition-all"
                >
                  {category.image && (
                    <img
                      src={category.image}
                      alt={category.name}
                      className="mx-auto h-16 w-16 object-contain"
                    />
                  )}
                  <p className="mt-2 font-medium text-gray-900 group-hover:text-primary-600">
                    {category.name}
                  </p>
                </Link>
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Featured Products Section */}
      <section className="bg-gray-50 py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between">
            <h2 className="text-2xl font-bold text-gray-900">Featured Products</h2>
            <Link
              to="/products?featured=true"
              className="text-primary-600 hover:text-primary-700"
            >
              View all
            </Link>
          </div>
          {loadingProducts ? (
            <LoadingSpinner />
          ) : (
            <div className="mt-8 grid grid-cols-1 gap-6 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4">
              {featuredProducts?.map((product) => (
                <ProductCard key={product.id} product={product} />
              ))}
            </div>
          )}
        </div>
      </section>

      {/* Features Section */}
      <section className="py-16">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
          <div className="grid grid-cols-1 gap-8 md:grid-cols-3">
            <div className="text-center">
              <div className="mx-auto h-12 w-12 rounded-full bg-primary-100 flex items-center justify-center">
                <span className="text-2xl">🚚</span>
              </div>
              <h3 className="mt-4 font-semibold text-gray-900">Free Shipping</h3>
              <p className="mt-2 text-sm text-gray-600">
                Free shipping on orders over $50
              </p>
            </div>
            <div className="text-center">
              <div className="mx-auto h-12 w-12 rounded-full bg-primary-100 flex items-center justify-center">
                <span className="text-2xl">🔄</span>
              </div>
              <h3 className="mt-4 font-semibold text-gray-900">Easy Returns</h3>
              <p className="mt-2 text-sm text-gray-600">
                30-day hassle-free returns
              </p>
            </div>
            <div className="text-center">
              <div className="mx-auto h-12 w-12 rounded-full bg-primary-100 flex items-center justify-center">
                <span className="text-2xl">🔒</span>
              </div>
              <h3 className="mt-4 font-semibold text-gray-900">Secure Payment</h3>
              <p className="mt-2 text-sm text-gray-600">
                100% secure checkout
              </p>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
