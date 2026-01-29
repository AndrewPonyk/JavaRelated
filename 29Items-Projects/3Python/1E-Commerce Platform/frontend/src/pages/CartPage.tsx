import { Link } from 'react-router-dom';
import { ShoppingBag, Trash2, Plus, Minus, ArrowRight, Tag } from 'lucide-react';
import { useCart } from '../hooks/useCart';
import { formatPrice } from '../utils/formatters';
import LoadingSpinner from '../components/common/LoadingSpinner';
import { useState } from 'react';

export default function CartPage() {
  const {
    cart,
    isLoading,
    isEmpty,
    subtotal,
    updateQuantity,
    removeItem,
    clearCart,
    isUpdating,
    isRemoving,
    isClearing,
  } = useCart();

  const isPending = isUpdating || isRemoving || isClearing;

  const [couponCode, setCouponCode] = useState('');
  const [couponError, setCouponError] = useState('');

  if (isLoading) {
    return (
      <div className="flex justify-center items-center min-h-[60vh]">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  if (isEmpty) {
    return (
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16 text-center">
        <ShoppingBag className="mx-auto h-16 w-16 text-gray-400" />
        <h2 className="mt-4 text-2xl font-bold text-gray-900">Your cart is empty</h2>
        <p className="mt-2 text-gray-600">
          Looks like you haven't added any items to your cart yet.
        </p>
        <Link
          to="/products"
          className="mt-8 inline-flex items-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
        >
          Start Shopping
          <ArrowRight className="ml-2 h-5 w-5" />
        </Link>
      </div>
    );
  }

  const handleUpdateQuantity = (itemId: number, newQuantity: number) => {
    if (newQuantity < 1) return;
    updateQuantity({ itemId, quantity: newQuantity });
  };

  const handleRemoveItem = (itemId: number) => {
    removeItem(itemId);
  };

  const handleApplyCoupon = () => {
    if (!couponCode.trim()) {
      setCouponError('Please enter a coupon code');
      return;
    }
    setCouponError('Coupon will be applied at checkout');
  };

  // Estimate tax and shipping
  const taxRate = 0.0825;
  const taxAmount = subtotal * taxRate;
  const shippingAmount = subtotal >= 50 ? 0 : 5.99;
  const total = subtotal + taxAmount + shippingAmount;

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">Shopping Cart</h1>

      <div className="lg:grid lg:grid-cols-12 lg:gap-x-12">
        {/* Cart Items */}
        <div className="lg:col-span-8">
          <div className="border rounded-lg divide-y">
            {cart?.items.map((item) => (
              <div key={item.id} className="p-6 flex gap-6">
                {/* Product Image */}
                <Link
                  to={`/products/${item.product.slug}`}
                  className="flex-shrink-0 w-24 h-24 bg-gray-100 rounded-lg overflow-hidden"
                >
                  <img
                    src={item.product.images?.[0]?.url || '/placeholder.jpg'}
                    alt={item.product.name}
                    className="w-full h-full object-cover"
                  />
                </Link>

                {/* Product Info */}
                <div className="flex-1 min-w-0">
                  <Link
                    to={`/products/${item.product.slug}`}
                    className="text-lg font-medium text-gray-900 hover:text-indigo-600"
                  >
                    {item.product.name}
                  </Link>

                  {item.variant && (
                    <p className="mt-1 text-sm text-gray-500">
                      {item.variant.name}
                    </p>
                  )}

                  <p className="mt-1 text-lg font-medium text-gray-900">
                    {formatPrice(item.unit_price)}
                  </p>

                  <div className="mt-4 flex items-center gap-6">
                    {/* Quantity Controls */}
                    <div className="flex items-center border rounded-md">
                      <button
                        onClick={() => handleUpdateQuantity(item.id, item.quantity - 1)}
                        disabled={isPending || item.quantity <= 1}
                        className="p-2 hover:bg-gray-50 disabled:opacity-50"
                      >
                        <Minus className="h-4 w-4" />
                      </button>
                      <span className="px-4 py-2 text-center min-w-[3rem]">
                        {item.quantity}
                      </span>
                      <button
                        onClick={() => handleUpdateQuantity(item.id, item.quantity + 1)}
                        disabled={isPending}
                        className="p-2 hover:bg-gray-50 disabled:opacity-50"
                      >
                        <Plus className="h-4 w-4" />
                      </button>
                    </div>

                    {/* Remove Button */}
                    <button
                      onClick={() => handleRemoveItem(item.id)}
                      disabled={isPending}
                      className="text-red-600 hover:text-red-700 flex items-center text-sm disabled:opacity-50"
                    >
                      <Trash2 className="h-4 w-4 mr-1" />
                      Remove
                    </button>
                  </div>
                </div>

                {/* Line Total */}
                <div className="text-right">
                  <p className="text-lg font-medium text-gray-900">
                    {formatPrice(item.line_total)}
                  </p>
                </div>
              </div>
            ))}
          </div>

          {/* Clear Cart */}
          <div className="mt-4 flex justify-end">
            <button
              onClick={() => clearCart()}
              disabled={isPending}
              className="text-sm text-gray-500 hover:text-gray-700 disabled:opacity-50"
            >
              Clear Cart
            </button>
          </div>
        </div>

        {/* Order Summary */}
        <div className="lg:col-span-4 mt-8 lg:mt-0">
          <div className="bg-gray-50 rounded-lg p-6 sticky top-24">
            <h2 className="text-lg font-medium text-gray-900 mb-6">Order Summary</h2>

            {/* Coupon Input */}
            <div className="mb-6">
              <label htmlFor="coupon" className="block text-sm font-medium text-gray-700 mb-2">
                Have a coupon?
              </label>
              <div className="flex gap-2">
                <div className="relative flex-1">
                  <Tag className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
                  <input
                    type="text"
                    id="coupon"
                    value={couponCode}
                    onChange={(e) => {
                      setCouponCode(e.target.value);
                      setCouponError('');
                    }}
                    placeholder="Enter code"
                    className="pl-10 w-full px-3 py-2 border rounded-md focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <button
                  onClick={handleApplyCoupon}
                  className="px-4 py-2 border border-indigo-600 text-indigo-600 rounded-md hover:bg-indigo-50"
                >
                  Apply
                </button>
              </div>
              {couponError && (
                <p className="mt-1 text-sm text-gray-500">{couponError}</p>
              )}
            </div>

            {/* Price Breakdown */}
            <div className="space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600">Subtotal</span>
                <span className="font-medium text-gray-900">{formatPrice(subtotal)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">Estimated Tax</span>
                <span className="font-medium text-gray-900">{formatPrice(taxAmount)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-600">Shipping</span>
                <span className="font-medium text-gray-900">
                  {shippingAmount === 0 ? (
                    <span className="text-green-600">FREE</span>
                  ) : (
                    formatPrice(shippingAmount)
                  )}
                </span>
              </div>
              {subtotal < 50 && (
                <p className="text-xs text-gray-500">
                  Add {formatPrice(50 - subtotal)} more for free shipping
                </p>
              )}
            </div>

            <div className="border-t mt-4 pt-4">
              <div className="flex justify-between text-lg font-medium">
                <span className="text-gray-900">Total</span>
                <span className="text-gray-900">{formatPrice(total)}</span>
              </div>
            </div>

            {/* Checkout Button */}
            <Link
              to="/checkout"
              className="mt-6 w-full flex items-center justify-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
            >
              Proceed to Checkout
              <ArrowRight className="ml-2 h-5 w-5" />
            </Link>

            {/* Continue Shopping */}
            <Link
              to="/products"
              className="mt-4 w-full flex items-center justify-center px-6 py-3 border border-gray-300 text-base font-medium rounded-md text-gray-700 hover:bg-gray-50"
            >
              Continue Shopping
            </Link>

            {/* Security Note */}
            <div className="mt-6 text-center text-xs text-gray-500">
              <p>Secure checkout powered by Stripe</p>
              <p className="mt-1">SSL encrypted for your protection</p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
