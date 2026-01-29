import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Star,
  ShoppingCart,
  Heart,
  Truck,
  Shield,
  RotateCcw,
  Minus,
  Plus,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { useProduct, useFeaturedProducts } from '../hooks/useProducts';
import { useCart } from '../hooks/useCart';
import { useWishlist } from '../hooks/useWishlist';
import { formatPrice } from '../utils/formatters';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ProductCard from '../components/products/ProductCard';

export default function ProductDetailPage() {
  const { slug } = useParams<{ slug: string }>();
  const { data: product, isLoading, error } = useProduct(slug || '');
  const { data: featuredProducts } = useFeaturedProducts(4);
  const { addItem, isPending } = useCart();
  const { isInWishlist, toggleWishlist, isUpdating: isWishlistUpdating } = useWishlist();

  const [quantity, setQuantity] = useState(1);
  const [selectedVariant, setSelectedVariant] = useState<number | null>(null);
  const [selectedImage, setSelectedImage] = useState(0);

  if (isLoading) {
    return (
      <div className="flex justify-center items-center min-h-[60vh]">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (error || !product) {
    return (
      <div className="max-w-7xl mx-auto px-4 py-16 text-center">
        <h1 className="text-2xl font-bold text-gray-900 mb-4">Product Not Found</h1>
        <p className="text-gray-600 mb-8">
          The product you're looking for doesn't exist or has been removed.
        </p>
        <Link
          to="/products"
          className="inline-flex items-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
        >
          Browse Products
        </Link>
      </div>
    );
  }

  const images = product.images?.length ? product.images : [{ url: '/placeholder.jpg', alt_text: product.name }];
  const hasDiscount = product.compare_at_price && product.compare_at_price > product.price;
  const discountPercentage = hasDiscount
    ? Math.round(((product.compare_at_price - product.price) / product.compare_at_price) * 100)
    : 0;

  const handleAddToCart = () => {
    addItem({
      productId: product.id,
      quantity,
      variantId: selectedVariant || undefined,
    });
  };

  const incrementQuantity = () => setQuantity(q => q + 1);
  const decrementQuantity = () => setQuantity(q => Math.max(1, q - 1));

  const nextImage = () => setSelectedImage(i => (i + 1) % images.length);
  const prevImage = () => setSelectedImage(i => (i - 1 + images.length) % images.length);

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Breadcrumb */}
      <nav className="flex mb-8" aria-label="Breadcrumb">
        <ol className="flex items-center space-x-2 text-sm text-gray-500">
          <li><Link to="/" className="hover:text-gray-700">Home</Link></li>
          <li>/</li>
          <li><Link to="/products" className="hover:text-gray-700">Products</Link></li>
          {product.category && (
            <>
              <li>/</li>
              <li>
                <Link to={`/products?category=${product.category.slug}`} className="hover:text-gray-700">
                  {product.category.name}
                </Link>
              </li>
            </>
          )}
          <li>/</li>
          <li className="text-gray-900 font-medium">{product.name}</li>
        </ol>
      </nav>

      <div className="lg:grid lg:grid-cols-2 lg:gap-x-8 lg:items-start">
        {/* Image Gallery */}
        <div className="flex flex-col">
          <div className="relative aspect-square overflow-hidden rounded-lg bg-gray-100">
            <img
              src={images[selectedImage]?.url || '/placeholder.jpg'}
              alt={images[selectedImage]?.alt_text || product.name}
              className="w-full h-full object-cover"
            />

            {images.length > 1 && (
              <>
                <button
                  onClick={prevImage}
                  className="absolute left-2 top-1/2 -translate-y-1/2 p-2 rounded-full bg-white/80 hover:bg-white shadow-lg"
                >
                  <ChevronLeft className="h-5 w-5" />
                </button>
                <button
                  onClick={nextImage}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-2 rounded-full bg-white/80 hover:bg-white shadow-lg"
                >
                  <ChevronRight className="h-5 w-5" />
                </button>
              </>
            )}

            {hasDiscount && (
              <span className="absolute top-4 left-4 bg-red-500 text-white px-3 py-1 rounded-full text-sm font-medium">
                -{discountPercentage}%
              </span>
            )}
          </div>

          {/* Thumbnail Gallery */}
          {images.length > 1 && (
            <div className="mt-4 grid grid-cols-4 gap-2">
              {images.map((image, idx) => (
                <button
                  key={idx}
                  onClick={() => setSelectedImage(idx)}
                  className={`aspect-square rounded-lg overflow-hidden ${
                    selectedImage === idx ? 'ring-2 ring-indigo-500' : 'ring-1 ring-gray-200'
                  }`}
                >
                  <img
                    src={image.url}
                    alt={image.alt_text || `${product.name} ${idx + 1}`}
                    className="w-full h-full object-cover"
                  />
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Product Info */}
        <div className="mt-10 lg:mt-0 lg:pl-8">
          <h1 className="text-3xl font-bold text-gray-900">{product.name}</h1>

          {/* Rating */}
          <div className="mt-3 flex items-center">
            <div className="flex items-center">
              {[1, 2, 3, 4, 5].map((star) => (
                <Star
                  key={star}
                  className={`h-5 w-5 ${
                    star <= Math.round(product.avg_rating || 0)
                      ? 'text-yellow-400 fill-current'
                      : 'text-gray-300'
                  }`}
                />
              ))}
            </div>
            <span className="ml-2 text-sm text-gray-600">
              {product.avg_rating?.toFixed(1) || '0'} ({product.review_count || 0} reviews)
            </span>
          </div>

          {/* Price */}
          <div className="mt-4">
            <div className="flex items-center gap-3">
              <span className="text-3xl font-bold text-gray-900">
                {formatPrice(product.price)}
              </span>
              {hasDiscount && (
                <span className="text-xl text-gray-500 line-through">
                  {formatPrice(product.compare_at_price)}
                </span>
              )}
            </div>
          </div>

          {/* Short Description */}
          {product.short_description && (
            <p className="mt-4 text-gray-600">{product.short_description}</p>
          )}

          {/* Variants */}
          {product.variants && product.variants.length > 0 && (
            <div className="mt-6">
              <h3 className="text-sm font-medium text-gray-900">Options</h3>
              <div className="mt-2 flex flex-wrap gap-2">
                {product.variants.map((variant) => (
                  <button
                    key={variant.id}
                    onClick={() => setSelectedVariant(variant.id)}
                    disabled={!variant.is_active}
                    className={`px-4 py-2 rounded-md border ${
                      selectedVariant === variant.id
                        ? 'border-indigo-600 bg-indigo-50 text-indigo-600'
                        : 'border-gray-300 hover:border-gray-400'
                    } ${!variant.is_active ? 'opacity-50 cursor-not-allowed' : ''}`}
                  >
                    {variant.name}
                    {variant.price_adjustment !== 0 && (
                      <span className="ml-1 text-sm">
                        ({variant.price_adjustment > 0 ? '+' : ''}{formatPrice(variant.price_adjustment)})
                      </span>
                    )}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Quantity */}
          <div className="mt-6">
            <h3 className="text-sm font-medium text-gray-900">Quantity</h3>
            <div className="mt-2 flex items-center">
              <button
                onClick={decrementQuantity}
                className="p-2 border rounded-l-md hover:bg-gray-50"
              >
                <Minus className="h-4 w-4" />
              </button>
              <input
                type="number"
                min="1"
                value={quantity}
                onChange={(e) => setQuantity(Math.max(1, parseInt(e.target.value) || 1))}
                className="w-16 text-center border-y py-2 focus:outline-none"
              />
              <button
                onClick={incrementQuantity}
                className="p-2 border rounded-r-md hover:bg-gray-50"
              >
                <Plus className="h-4 w-4" />
              </button>
            </div>
          </div>

          {/* Add to Cart */}
          <div className="mt-8 flex gap-4">
            <button
              onClick={handleAddToCart}
              disabled={isPending}
              className="flex-1 flex items-center justify-center px-8 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50"
            >
              <ShoppingCart className="h-5 w-5 mr-2" />
              {isPending ? 'Adding...' : 'Add to Cart'}
            </button>
            <button
              onClick={() => toggleWishlist(product.id)}
              disabled={isWishlistUpdating}
              className={`p-3 border rounded-md transition-colors disabled:opacity-50 ${
                isInWishlist(product.id)
                  ? 'border-red-300 bg-red-50 hover:bg-red-100'
                  : 'border-gray-300 hover:bg-gray-50'
              }`}
            >
              <Heart
                className={`h-6 w-6 ${
                  isInWishlist(product.id)
                    ? 'fill-red-500 text-red-500'
                    : 'text-gray-400 hover:text-red-500'
                }`}
              />
            </button>
          </div>

          {/* Features */}
          <div className="mt-8 border-t pt-8 space-y-4">
            <div className="flex items-center text-sm text-gray-600">
              <Truck className="h-5 w-5 mr-3 text-green-600" />
              Free shipping on orders over $50
            </div>
            <div className="flex items-center text-sm text-gray-600">
              <Shield className="h-5 w-5 mr-3 text-blue-600" />
              Secure payment
            </div>
            <div className="flex items-center text-sm text-gray-600">
              <RotateCcw className="h-5 w-5 mr-3 text-orange-600" />
              30-day return policy
            </div>
          </div>

          {/* Vendor */}
          {product.vendor && (
            <div className="mt-6 border-t pt-6">
              <p className="text-sm text-gray-600">
                Sold by{' '}
                <Link
                  to={`/vendors/${product.vendor.slug}`}
                  className="font-medium text-indigo-600 hover:text-indigo-500"
                >
                  {product.vendor.business_name}
                </Link>
              </p>
            </div>
          )}
        </div>
      </div>

      {/* Product Description */}
      <div className="mt-16 border-t pt-10">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">Description</h2>
        <div
          className="prose max-w-none text-gray-600"
          dangerouslySetInnerHTML={{ __html: product.description || 'No description available.' }}
        />
      </div>

      {/* Reviews Section */}
      <div className="mt-16 border-t pt-10">
        <h2 className="text-2xl font-bold text-gray-900 mb-6">
          Customer Reviews ({product.review_count || 0})
        </h2>

        {product.reviews && product.reviews.length > 0 ? (
          <div className="space-y-6">
            {product.reviews.map((review) => (
              <div key={review.id} className="border-b pb-6">
                <div className="flex items-center mb-2">
                  <div className="flex">
                    {[1, 2, 3, 4, 5].map((star) => (
                      <Star
                        key={star}
                        className={`h-4 w-4 ${
                          star <= review.rating
                            ? 'text-yellow-400 fill-current'
                            : 'text-gray-300'
                        }`}
                      />
                    ))}
                  </div>
                  <span className="ml-2 text-sm font-medium text-gray-900">
                    {review.title || 'Review'}
                  </span>
                  {review.is_verified_purchase && (
                    <span className="ml-2 text-xs bg-green-100 text-green-800 px-2 py-0.5 rounded">
                      Verified Purchase
                    </span>
                  )}
                </div>
                <p className="text-gray-600">{review.comment}</p>
                <p className="mt-2 text-sm text-gray-500">
                  By {review.user?.first_name || 'Anonymous'} on{' '}
                  {new Date(review.created_at).toLocaleDateString()}
                </p>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-gray-500">No reviews yet. Be the first to review this product!</p>
        )}
      </div>

      {/* Related Products */}
      {featuredProducts && featuredProducts.length > 0 && (
        <div className="mt-16 border-t pt-10">
          <h2 className="text-2xl font-bold text-gray-900 mb-6">You May Also Like</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-6">
            {featuredProducts.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
