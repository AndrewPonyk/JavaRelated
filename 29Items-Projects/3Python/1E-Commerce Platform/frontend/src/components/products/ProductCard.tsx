/**
 * Product card component for product listings
 */
import { Link } from 'react-router-dom';
import { ShoppingCart, Heart } from 'lucide-react';
import { formatPrice } from '../../utils/formatters';
import { cn } from '../../utils/cn';
import { useCart } from '../../hooks/useCart';
import { useWishlist } from '../../hooks/useWishlist';
import type { ProductListItem } from '../../types';

interface ProductCardProps {
  product: ProductListItem;
  className?: string;
}

export default function ProductCard({ product, className }: ProductCardProps) {
  const { addItem, isAddingItem } = useCart();
  const { isInWishlist, toggleWishlist, isUpdating } = useWishlist();

  const inWishlist = isInWishlist(product.id);

  const handleAddToCart = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      await addItem({ productId: product.id });
    } catch (error) {
      console.error('Failed to add to cart:', error);
    }
  };

  const handleToggleWishlist = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    try {
      await toggleWishlist(product.id);
    } catch (error) {
      console.error('Failed to toggle wishlist:', error);
    }
  };

  return (
    <div className={cn('card group overflow-hidden', className)}>
      <Link to={`/products/${product.slug}`}>
        {/* Image */}
        <div className="relative aspect-square overflow-hidden bg-gray-100">
          {product.primary_image ? (
            <img
              src={product.primary_image}
              alt={product.name}
              className="h-full w-full object-cover transition-transform group-hover:scale-105"
              loading="lazy"
            />
          ) : (
            <div className="flex h-full items-center justify-center text-gray-400">
              No image
            </div>
          )}

          {/* Discount badge */}
          {product.discount_percentage > 0 && (
            <span className="absolute left-2 top-2 rounded bg-red-500 px-2 py-1 text-xs font-semibold text-white">
              -{product.discount_percentage}%
            </span>
          )}

          {/* Featured badge */}
          {product.is_featured && (
            <span className="absolute right-2 top-2 rounded bg-primary-500 px-2 py-1 text-xs font-semibold text-white">
              Featured
            </span>
          )}

          {/* Quick actions (visible on hover) */}
          <div className="absolute bottom-2 right-2 flex gap-2 opacity-0 transition-opacity group-hover:opacity-100">
            <button
              onClick={handleAddToCart}
              disabled={isAddingItem}
              className="rounded-full bg-white p-2 shadow-md hover:bg-gray-100 disabled:opacity-50"
              aria-label="Add to cart"
            >
              <ShoppingCart className="h-4 w-4" />
            </button>
            <button
              onClick={handleToggleWishlist}
              disabled={isUpdating}
              className={cn(
                "rounded-full p-2 shadow-md transition-colors disabled:opacity-50",
                inWishlist ? "bg-red-50 hover:bg-red-100" : "bg-white hover:bg-gray-100"
              )}
              aria-label={inWishlist ? "Remove from wishlist" : "Add to wishlist"}
            >
              <Heart className={cn("h-4 w-4", inWishlist && "fill-red-500 text-red-500")} />
            </button>
          </div>
        </div>

        {/* Details */}
        <div className="p-4">
          <p className="text-xs text-gray-500">{product.category_name}</p>
          <h3 className="mt-1 font-medium text-gray-900 line-clamp-2">{product.name}</h3>

          {/* Rating */}
          {product.avg_rating && (
            <div className="mt-1 flex items-center gap-1">
              <span className="text-yellow-400">★</span>
              <span className="text-sm text-gray-600">{product.avg_rating.toFixed(1)}</span>
            </div>
          )}

          {/* Price */}
          <div className="mt-2 flex items-center gap-2">
            <span className="font-semibold text-gray-900">
              {formatPrice(product.price)}
            </span>
            {product.compare_at_price && (
              <span className="text-sm text-gray-500 line-through">
                {formatPrice(product.compare_at_price)}
              </span>
            )}
          </div>
        </div>
      </Link>
    </div>
  );
}
