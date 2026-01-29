import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { CreditCard, Truck, Shield, ArrowLeft, CheckCircle, AlertCircle } from 'lucide-react';
import { useCart } from '../hooks/useCart';
import { useAuth } from '../hooks/useAuth';
import { formatPrice } from '../utils/formatters';
import LoadingSpinner from '../components/common/LoadingSpinner';
import api from '../services/api';

interface CheckoutData {
  shipping_address: {
    full_name: string;
    street_address: string;
    apartment: string;
    city: string;
    state: string;
    postal_code: string;
    country: string;
    phone: string;
  };
  same_as_shipping: boolean;
  billing_address?: {
    full_name: string;
    street_address: string;
    apartment: string;
    city: string;
    state: string;
    postal_code: string;
    country: string;
    phone: string;
  };
  coupon_code: string;
}

const emptyAddress = {
  full_name: '',
  street_address: '',
  apartment: '',
  city: '',
  state: '',
  postal_code: '',
  country: 'US',
  phone: '',
};

export default function CheckoutPage() {
  const navigate = useNavigate();
  const { isAuthenticated, user } = useAuth();
  const { cart, isEmpty, subtotal, clearCart } = useCart();

  const [step, setStep] = useState(1);
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState('');
  const [orderComplete, setOrderComplete] = useState(false);
  const [orderId, setOrderId] = useState('');

  const [checkoutData, setCheckoutData] = useState<CheckoutData>({
    shipping_address: {
      ...emptyAddress,
      full_name: user ? `${user.first_name} ${user.last_name}`.trim() : '',
    },
    same_as_shipping: true,
    coupon_code: '',
  });

  const [clientSecret, setClientSecret] = useState('');
  const [isDemoMode, setIsDemoMode] = useState(false);
  const [totals, setTotals] = useState({
    subtotal: 0,
    tax_amount: 0,
    shipping_amount: 0,
    discount_amount: 0,
    total: 0,
  });

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: '/checkout' } } });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (isEmpty) {
      navigate('/cart');
    }
  }, [isEmpty, navigate]);

  if (!isAuthenticated || isEmpty) {
    return (
      <div className="flex justify-center items-center min-h-[60vh]">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  const handleAddressChange = (
    type: 'shipping' | 'billing',
    field: string,
    value: string
  ) => {
    setCheckoutData((prev) => ({
      ...prev,
      [`${type}_address`]: {
        ...(prev[`${type}_address` as keyof CheckoutData] as object),
        [field]: value,
      },
    }));
  };

  const validateAddress = (address: typeof emptyAddress): string[] => {
    const errors: string[] = [];
    if (!address.full_name) errors.push('Full name is required');
    if (!address.street_address) errors.push('Street address is required');
    if (!address.city) errors.push('City is required');
    if (!address.state) errors.push('State is required');
    if (!address.postal_code) errors.push('Postal code is required');
    return errors;
  };

  const handleContinueToPayment = async () => {
    const addressErrors = validateAddress(checkoutData.shipping_address);
    if (addressErrors.length > 0) {
      setError(addressErrors.join(', '));
      return;
    }

    setIsProcessing(true);
    setError('');

    try {
      const response = await api.post('/checkout/initiate/', checkoutData);
      const data = response.data;

      setClientSecret(data.client_secret);
      setIsDemoMode(data.is_demo_mode || false);
      setTotals({
        subtotal: parseFloat(data.subtotal),
        tax_amount: parseFloat(data.tax_amount),
        shipping_amount: parseFloat(data.shipping_amount),
        discount_amount: parseFloat(data.discount_amount),
        total: parseFloat(data.total),
      });
      setStep(2);
    } catch (err: unknown) {
      const error = err as { response?: { data?: { detail?: string; error?: string } } };
      setError(error.response?.data?.error || error.response?.data?.detail || 'Failed to initiate checkout');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleConfirmPayment = async () => {
    setIsProcessing(true);
    setError('');

    try {
      const response = await api.post('/checkout/confirm/', {
        payment_intent_id: clientSecret,
      });

      setOrderId(response.data.id);
      setOrderComplete(true);
      clearCart();
    } catch (err: unknown) {
      const error = err as { response?: { data?: { detail?: string } } };
      setError(error.response?.data?.detail || 'Payment failed');
    } finally {
      setIsProcessing(false);
    }
  };

  if (orderComplete) {
    return (
      <div className="max-w-2xl mx-auto px-4 py-16 text-center">
        <CheckCircle className="mx-auto h-16 w-16 text-green-500" />
        <h1 className="mt-4 text-3xl font-bold text-gray-900">Order Confirmed!</h1>
        <p className="mt-2 text-gray-600">
          Thank you for your purchase. Your order number is:
        </p>
        <p className="mt-2 text-lg font-mono font-bold text-indigo-600">
          {orderId}
        </p>
        <p className="mt-4 text-gray-600">
          We've sent a confirmation email with your order details.
        </p>
        <div className="mt-8 flex gap-4 justify-center">
          <Link
            to={`/orders/${orderId}`}
            className="px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
          >
            View Order
          </Link>
          <Link
            to="/products"
            className="px-6 py-3 border border-gray-300 text-base font-medium rounded-md text-gray-700 hover:bg-gray-50"
          >
            Continue Shopping
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      {/* Header */}
      <div className="mb-8">
        <Link
          to="/cart"
          className="inline-flex items-center text-sm text-gray-500 hover:text-gray-700"
        >
          <ArrowLeft className="h-4 w-4 mr-1" />
          Back to Cart
        </Link>
        <h1 className="mt-4 text-3xl font-bold text-gray-900">Checkout</h1>
      </div>

      {/* Progress Steps */}
      <div className="mb-8">
        <div className="flex items-center justify-center">
          <div className={`flex items-center ${step >= 1 ? 'text-indigo-600' : 'text-gray-400'}`}>
            <span className={`w-8 h-8 rounded-full flex items-center justify-center ${step >= 1 ? 'bg-indigo-600 text-white' : 'bg-gray-200'}`}>
              1
            </span>
            <span className="ml-2 font-medium">Shipping</span>
          </div>
          <div className={`w-24 h-1 mx-4 ${step >= 2 ? 'bg-indigo-600' : 'bg-gray-200'}`} />
          <div className={`flex items-center ${step >= 2 ? 'text-indigo-600' : 'text-gray-400'}`}>
            <span className={`w-8 h-8 rounded-full flex items-center justify-center ${step >= 2 ? 'bg-indigo-600 text-white' : 'bg-gray-200'}`}>
              2
            </span>
            <span className="ml-2 font-medium">Payment</span>
          </div>
        </div>
      </div>

      {error && (
        <div className="mb-6 rounded-md bg-red-50 p-4">
          <div className="flex">
            <AlertCircle className="h-5 w-5 text-red-400" />
            <p className="ml-3 text-sm font-medium text-red-800">{error}</p>
          </div>
        </div>
      )}

      <div className="lg:grid lg:grid-cols-12 lg:gap-x-12">
        {/* Main Content */}
        <div className="lg:col-span-7">
          {step === 1 && (
            <div className="bg-white rounded-lg border p-6">
              <h2 className="text-lg font-medium text-gray-900 mb-6 flex items-center">
                <Truck className="h-5 w-5 mr-2" />
                Shipping Address
              </h2>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700">Full Name</label>
                  <input
                    type="text"
                    value={checkoutData.shipping_address.full_name}
                    onChange={(e) => handleAddressChange('shipping', 'full_name', e.target.value)}
                    className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">Street Address</label>
                  <input
                    type="text"
                    value={checkoutData.shipping_address.street_address}
                    onChange={(e) => handleAddressChange('shipping', 'street_address', e.target.value)}
                    className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                <div>
                  <label className="block text-sm font-medium text-gray-700">Apt, Suite, etc. (optional)</label>
                  <input
                    type="text"
                    value={checkoutData.shipping_address.apartment}
                    onChange={(e) => handleAddressChange('shipping', 'apartment', e.target.value)}
                    className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700">City</label>
                    <input
                      type="text"
                      value={checkoutData.shipping_address.city}
                      onChange={(e) => handleAddressChange('shipping', 'city', e.target.value)}
                      className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700">State</label>
                    <input
                      type="text"
                      value={checkoutData.shipping_address.state}
                      onChange={(e) => handleAddressChange('shipping', 'state', e.target.value)}
                      className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                      placeholder="e.g., CA"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700">ZIP Code</label>
                    <input
                      type="text"
                      value={checkoutData.shipping_address.postal_code}
                      onChange={(e) => handleAddressChange('shipping', 'postal_code', e.target.value)}
                      className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700">Phone</label>
                    <input
                      type="tel"
                      value={checkoutData.shipping_address.phone}
                      onChange={(e) => handleAddressChange('shipping', 'phone', e.target.value)}
                      className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                    />
                  </div>
                </div>
              </div>

              {/* Coupon Code */}
              <div className="mt-6 pt-6 border-t">
                <label className="block text-sm font-medium text-gray-700">Coupon Code (optional)</label>
                <input
                  type="text"
                  value={checkoutData.coupon_code}
                  onChange={(e) => setCheckoutData(prev => ({ ...prev, coupon_code: e.target.value }))}
                  placeholder="Enter coupon code"
                  className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
                />
              </div>

              <button
                onClick={handleContinueToPayment}
                disabled={isProcessing}
                className="mt-6 w-full flex items-center justify-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50"
              >
                {isProcessing ? 'Processing...' : 'Continue to Payment'}
              </button>
            </div>
          )}

          {step === 2 && (
            <div className="bg-white rounded-lg border p-6">
              <h2 className="text-lg font-medium text-gray-900 mb-6 flex items-center">
                <CreditCard className="h-5 w-5 mr-2" />
                Payment
              </h2>

              {isDemoMode && (
                <div className="bg-yellow-50 border border-yellow-200 rounded-lg p-4 mb-6">
                  <div className="flex items-start">
                    <AlertCircle className="h-5 w-5 text-yellow-600 mt-0.5" />
                    <div className="ml-3">
                      <h3 className="text-sm font-medium text-yellow-800">Demo Mode</h3>
                      <p className="text-sm text-yellow-700 mt-1">
                        Stripe is not configured. This checkout is running in demo mode - no real payment will be processed.
                        Click "Complete Order" to simulate a successful purchase.
                      </p>
                    </div>
                  </div>
                </div>
              )}

              <div className="bg-gray-50 rounded-lg p-4 mb-6">
                <p className="text-sm text-gray-600 mb-4">
                  {isDemoMode ? 'Demo payment form (no real charges)' : 'This is a demo checkout. In production, Stripe Elements would be integrated here.'}
                </p>
                <div className="space-y-4">
                  <div>
                    <label className="block text-sm font-medium text-gray-700">Card Number</label>
                    <input
                      type="text"
                      placeholder="4242 4242 4242 4242"
                      disabled
                      className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md bg-white"
                    />
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm font-medium text-gray-700">Expiry</label>
                      <input
                        type="text"
                        placeholder="12/25"
                        disabled
                        className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md bg-white"
                      />
                    </div>
                    <div>
                      <label className="block text-sm font-medium text-gray-700">CVC</label>
                      <input
                        type="text"
                        placeholder="123"
                        disabled
                        className="mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md bg-white"
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div className="flex gap-4">
                <button
                  onClick={() => setStep(1)}
                  className="flex-1 px-6 py-3 border border-gray-300 text-base font-medium rounded-md text-gray-700 hover:bg-gray-50"
                >
                  Back
                </button>
                <button
                  onClick={handleConfirmPayment}
                  disabled={isProcessing}
                  className={`flex-1 flex items-center justify-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white disabled:opacity-50 ${isDemoMode ? 'bg-yellow-600 hover:bg-yellow-700' : 'bg-indigo-600 hover:bg-indigo-700'}`}
                >
                  <Shield className="h-5 w-5 mr-2" />
                  {isProcessing ? 'Processing...' : isDemoMode ? `Complete Demo Order (${formatPrice(totals.total)})` : `Pay ${formatPrice(totals.total)}`}
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Order Summary Sidebar */}
        <div className="lg:col-span-5 mt-8 lg:mt-0">
          <div className="bg-gray-50 rounded-lg p-6 sticky top-24">
            <h2 className="text-lg font-medium text-gray-900 mb-6">Order Summary</h2>

            {/* Items */}
            <div className="space-y-4 max-h-64 overflow-y-auto">
              {cart?.items.map((item) => (
                <div key={item.id} className="flex gap-4">
                  <div className="w-16 h-16 bg-gray-200 rounded-md overflow-hidden flex-shrink-0">
                    <img
                      src={item.product.images?.[0]?.url || '/placeholder.jpg'}
                      alt={item.product.name}
                      className="w-full h-full object-cover"
                    />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">
                      {item.product.name}
                    </p>
                    <p className="text-sm text-gray-500">Qty: {item.quantity}</p>
                  </div>
                  <p className="text-sm font-medium text-gray-900">
                    {formatPrice(item.line_total)}
                  </p>
                </div>
              ))}
            </div>

            {/* Totals */}
            <div className="border-t mt-6 pt-6 space-y-3 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-600">Subtotal</span>
                <span className="font-medium text-gray-900">
                  {formatPrice(step === 2 ? totals.subtotal : subtotal)}
                </span>
              </div>
              {step === 2 && (
                <>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Tax</span>
                    <span className="font-medium text-gray-900">
                      {formatPrice(totals.tax_amount)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-gray-600">Shipping</span>
                    <span className="font-medium text-gray-900">
                      {totals.shipping_amount === 0 ? (
                        <span className="text-green-600">FREE</span>
                      ) : (
                        formatPrice(totals.shipping_amount)
                      )}
                    </span>
                  </div>
                  {totals.discount_amount > 0 && (
                    <div className="flex justify-between text-green-600">
                      <span>Discount</span>
                      <span>-{formatPrice(totals.discount_amount)}</span>
                    </div>
                  )}
                </>
              )}
            </div>

            <div className="border-t mt-4 pt-4">
              <div className="flex justify-between text-lg font-medium">
                <span className="text-gray-900">Total</span>
                <span className="text-gray-900">
                  {formatPrice(step === 2 ? totals.total : subtotal)}
                </span>
              </div>
            </div>

            {/* Security */}
            <div className="mt-6 flex items-center justify-center text-xs text-gray-500">
              <Shield className="h-4 w-4 mr-1" />
              Secure checkout - SSL encrypted
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
