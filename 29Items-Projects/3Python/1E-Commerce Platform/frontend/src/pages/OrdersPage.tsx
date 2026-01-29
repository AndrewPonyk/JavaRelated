import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Package, ChevronRight, Truck, CheckCircle, XCircle, Clock } from 'lucide-react';
import { useAuth } from '../hooks/useAuth';
import { formatPrice, formatDate } from '../utils/formatters';
import LoadingSpinner from '../components/common/LoadingSpinner';
import api from '../services/api';

interface Order {
  id: string;
  order_number: string;
  status: string;
  payment_status: string;
  total: number;
  created_at: string;
  shipped_at?: string;
  delivered_at?: string;
  items: {
    id: number;
    product_name: string;
    quantity: number;
    unit_price: number;
    line_total: number;
  }[];
}

const statusConfig: Record<string, { color: string; icon: React.ElementType; label: string }> = {
  pending: { color: 'yellow', icon: Clock, label: 'Pending' },
  confirmed: { color: 'blue', icon: Package, label: 'Confirmed' },
  processing: { color: 'indigo', icon: Package, label: 'Processing' },
  shipped: { color: 'purple', icon: Truck, label: 'Shipped' },
  delivered: { color: 'green', icon: CheckCircle, label: 'Delivered' },
  cancelled: { color: 'red', icon: XCircle, label: 'Cancelled' },
  refunded: { color: 'gray', icon: XCircle, label: 'Refunded' },
};

export default function OrdersPage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();
  const [orders, setOrders] = useState<Order[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [selectedOrder, setSelectedOrder] = useState<Order | null>(null);
  const [filter, setFilter] = useState<string>('all');

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: { pathname: '/orders' } } });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    fetchOrders();
  }, [filter]);

  const fetchOrders = async () => {
    setIsLoading(true);
    try {
      const params = filter !== 'all' ? { status: filter } : {};
      const response = await api.get('/orders/', { params });
      setOrders(response.data.results || response.data);
    } catch {
      console.error('Failed to fetch orders');
    } finally {
      setIsLoading(false);
    }
  };

  if (!isAuthenticated) {
    return (
      <div className="flex justify-center items-center min-h-[60vh]">
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  const getStatusBadge = (status: string) => {
    const config = statusConfig[status] || statusConfig.pending;
    const Icon = config.icon;

    const colorClasses: Record<string, string> = {
      yellow: 'bg-yellow-100 text-yellow-800',
      blue: 'bg-blue-100 text-blue-800',
      indigo: 'bg-indigo-100 text-indigo-800',
      purple: 'bg-purple-100 text-purple-800',
      green: 'bg-green-100 text-green-800',
      red: 'bg-red-100 text-red-800',
      gray: 'bg-gray-100 text-gray-800',
    };

    return (
      <span
        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colorClasses[config.color]}`}
      >
        <Icon className="h-3 w-3 mr-1" />
        {config.label}
      </span>
    );
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div className="flex justify-between items-center mb-8">
        <h1 className="text-3xl font-bold text-gray-900">My Orders</h1>

        {/* Filter */}
        <select
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"
        >
          <option value="all">All Orders</option>
          <option value="pending">Pending</option>
          <option value="processing">Processing</option>
          <option value="shipped">Shipped</option>
          <option value="delivered">Delivered</option>
          <option value="cancelled">Cancelled</option>
        </select>
      </div>

      {isLoading ? (
        <div className="flex justify-center items-center py-16">
          <LoadingSpinner size="lg" />
        </div>
      ) : orders.length === 0 ? (
        <div className="text-center py-16">
          <Package className="mx-auto h-16 w-16 text-gray-400" />
          <h2 className="mt-4 text-xl font-medium text-gray-900">No orders yet</h2>
          <p className="mt-2 text-gray-600">
            When you make a purchase, your orders will appear here.
          </p>
          <Link
            to="/products"
            className="mt-8 inline-flex items-center px-6 py-3 border border-transparent text-base font-medium rounded-md text-white bg-indigo-600 hover:bg-indigo-700"
          >
            Start Shopping
          </Link>
        </div>
      ) : (
        <div className="space-y-4">
          {orders.map((order) => (
            <div
              key={order.id}
              className="bg-white border rounded-lg overflow-hidden hover:shadow-md transition-shadow"
            >
              {/* Order Header */}
              <div className="px-6 py-4 bg-gray-50 border-b flex flex-wrap items-center justify-between gap-4">
                <div className="flex items-center gap-6 text-sm">
                  <div>
                    <span className="text-gray-500">Order</span>
                    <p className="font-mono font-medium text-gray-900">{order.order_number}</p>
                  </div>
                  <div>
                    <span className="text-gray-500">Placed on</span>
                    <p className="font-medium text-gray-900">{formatDate(order.created_at)}</p>
                  </div>
                  <div>
                    <span className="text-gray-500">Total</span>
                    <p className="font-medium text-gray-900">{formatPrice(order.total)}</p>
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  {getStatusBadge(order.status)}
                  <button
                    onClick={() => setSelectedOrder(selectedOrder?.id === order.id ? null : order)}
                    className="text-indigo-600 hover:text-indigo-500 text-sm font-medium flex items-center"
                  >
                    {selectedOrder?.id === order.id ? 'Hide Details' : 'View Details'}
                    <ChevronRight
                      className={`h-4 w-4 ml-1 transition-transform ${
                        selectedOrder?.id === order.id ? 'rotate-90' : ''
                      }`}
                    />
                  </button>
                </div>
              </div>

              {/* Order Items Preview */}
              <div className="px-6 py-4">
                <div className="flex items-center gap-4">
                  <div className="flex -space-x-2">
                    {order.items.slice(0, 4).map((item, idx) => (
                      <div
                        key={item.id}
                        className="w-10 h-10 rounded-full bg-gray-200 border-2 border-white flex items-center justify-center text-xs font-medium text-gray-600"
                        style={{ zIndex: 4 - idx }}
                      >
                        {item.quantity}
                      </div>
                    ))}
                    {order.items.length > 4 && (
                      <div className="w-10 h-10 rounded-full bg-gray-100 border-2 border-white flex items-center justify-center text-xs font-medium text-gray-600">
                        +{order.items.length - 4}
                      </div>
                    )}
                  </div>
                  <p className="text-sm text-gray-600">
                    {order.items.length} item{order.items.length > 1 ? 's' : ''}
                  </p>
                </div>
              </div>

              {/* Expanded Order Details */}
              {selectedOrder?.id === order.id && (
                <div className="px-6 py-4 border-t bg-gray-50">
                  <h3 className="text-sm font-medium text-gray-900 mb-4">Order Items</h3>
                  <div className="space-y-3">
                    {order.items.map((item) => (
                      <div key={item.id} className="flex justify-between items-center">
                        <div className="flex items-center gap-3">
                          <div className="w-12 h-12 bg-gray-200 rounded-md" />
                          <div>
                            <p className="text-sm font-medium text-gray-900">{item.product_name}</p>
                            <p className="text-sm text-gray-500">
                              Qty: {item.quantity} x {formatPrice(item.unit_price)}
                            </p>
                          </div>
                        </div>
                        <p className="text-sm font-medium text-gray-900">
                          {formatPrice(item.line_total)}
                        </p>
                      </div>
                    ))}
                  </div>

                  {/* Timeline */}
                  <div className="mt-6 pt-6 border-t">
                    <h3 className="text-sm font-medium text-gray-900 mb-4">Order Timeline</h3>
                    <div className="space-y-3">
                      <div className="flex items-center text-sm">
                        <CheckCircle className="h-4 w-4 text-green-500 mr-2" />
                        <span className="text-gray-600">Order placed</span>
                        <span className="ml-auto text-gray-500">{formatDate(order.created_at)}</span>
                      </div>
                      {order.shipped_at && (
                        <div className="flex items-center text-sm">
                          <Truck className="h-4 w-4 text-blue-500 mr-2" />
                          <span className="text-gray-600">Shipped</span>
                          <span className="ml-auto text-gray-500">{formatDate(order.shipped_at)}</span>
                        </div>
                      )}
                      {order.delivered_at && (
                        <div className="flex items-center text-sm">
                          <CheckCircle className="h-4 w-4 text-green-500 mr-2" />
                          <span className="text-gray-600">Delivered</span>
                          <span className="ml-auto text-gray-500">{formatDate(order.delivered_at)}</span>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Actions */}
                  <div className="mt-6 pt-6 border-t flex gap-4">
                    <Link
                      to={`/orders/${order.id}`}
                      className="text-sm font-medium text-indigo-600 hover:text-indigo-500"
                    >
                      View Full Details
                    </Link>
                    {['pending', 'confirmed'].includes(order.status) && (
                      <button className="text-sm font-medium text-red-600 hover:text-red-500">
                        Cancel Order
                      </button>
                    )}
                    {order.status === 'delivered' && (
                      <button className="text-sm font-medium text-indigo-600 hover:text-indigo-500">
                        Write a Review
                      </button>
                    )}
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
