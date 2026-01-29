/**
 * Cart drawer/sidebar component
 */
import { Fragment } from 'react';
import { Link } from 'react-router-dom';
import { Dialog, Transition } from '@headlessui/react';
import { X, Minus, Plus, Trash2 } from 'lucide-react';
import { useCart } from '../../hooks/useCart';
import { formatPrice } from '../../utils/formatters';

interface CartDrawerProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function CartDrawer({ isOpen, onClose }: CartDrawerProps) {
  const { cart, updateQuantity, removeItem, isUpdating, isRemoving } = useCart();

  return (
    <Transition show={isOpen} as={Fragment}>
      <Dialog as="div" className="relative z-50" onClose={onClose}>
        {/* Backdrop */}
        <Transition.Child
          as={Fragment}
          enter="ease-in-out duration-300"
          enterFrom="opacity-0"
          enterTo="opacity-100"
          leave="ease-in-out duration-300"
          leaveFrom="opacity-100"
          leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black/25" />
        </Transition.Child>

        <div className="fixed inset-0 overflow-hidden">
          <div className="absolute inset-0 overflow-hidden">
            <div className="pointer-events-none fixed inset-y-0 right-0 flex max-w-full pl-10">
              <Transition.Child
                as={Fragment}
                enter="transform transition ease-in-out duration-300"
                enterFrom="translate-x-full"
                enterTo="translate-x-0"
                leave="transform transition ease-in-out duration-300"
                leaveFrom="translate-x-0"
                leaveTo="translate-x-full"
              >
                <Dialog.Panel className="pointer-events-auto w-screen max-w-md">
                  <div className="flex h-full flex-col bg-white shadow-xl">
                    {/* Header */}
                    <div className="flex items-center justify-between border-b px-4 py-4">
                      <Dialog.Title className="text-lg font-semibold">
                        Shopping Cart ({cart?.item_count || 0})
                      </Dialog.Title>
                      <button
                        onClick={onClose}
                        className="rounded-full p-2 hover:bg-gray-100"
                      >
                        <X className="h-5 w-5" />
                      </button>
                    </div>

                    {/* Cart items */}
                    <div className="flex-1 overflow-y-auto px-4 py-4">
                      {cart?.is_empty ? (
                        <div className="flex h-full flex-col items-center justify-center text-center">
                          <p className="text-gray-500">Your cart is empty</p>
                          <Link
                            to="/products"
                            onClick={onClose}
                            className="btn-primary btn-md mt-4"
                          >
                            Continue Shopping
                          </Link>
                        </div>
                      ) : (
                        <ul className="space-y-4">
                          {cart?.items.map((item) => (
                            <li key={item.id} className="flex gap-4">
                              {/* Image */}
                              <div className="h-20 w-20 flex-shrink-0 overflow-hidden rounded-md bg-gray-100">
                                {item.product.primary_image ? (
                                  <img
                                    src={item.product.primary_image}
                                    alt={item.product.name}
                                    className="h-full w-full object-cover"
                                  />
                                ) : (
                                  <div className="flex h-full items-center justify-center text-xs text-gray-400">
                                    No image
                                  </div>
                                )}
                              </div>

                              {/* Details */}
                              <div className="flex flex-1 flex-col">
                                <Link
                                  to={`/products/${item.product.slug}`}
                                  onClick={onClose}
                                  className="font-medium text-gray-900 hover:text-primary-600"
                                >
                                  {item.product.name}
                                </Link>
                                {item.variant && (
                                  <p className="text-sm text-gray-500">
                                    {item.variant.name}
                                  </p>
                                )}
                                <p className="mt-1 font-medium">
                                  {formatPrice(item.unit_price)}
                                </p>

                                {/* Quantity controls */}
                                <div className="mt-2 flex items-center gap-2">
                                  <button
                                    onClick={() =>
                                      updateQuantity({
                                        itemId: item.id,
                                        quantity: Math.max(1, item.quantity - 1),
                                      })
                                    }
                                    disabled={isUpdating || item.quantity <= 1}
                                    className="rounded border p-1 hover:bg-gray-100 disabled:opacity-50"
                                  >
                                    <Minus className="h-4 w-4" />
                                  </button>
                                  <span className="w-8 text-center">
                                    {item.quantity}
                                  </span>
                                  <button
                                    onClick={() =>
                                      updateQuantity({
                                        itemId: item.id,
                                        quantity: item.quantity + 1,
                                      })
                                    }
                                    disabled={isUpdating}
                                    className="rounded border p-1 hover:bg-gray-100 disabled:opacity-50"
                                  >
                                    <Plus className="h-4 w-4" />
                                  </button>
                                  <button
                                    onClick={() => removeItem(item.id)}
                                    disabled={isRemoving}
                                    className="ml-auto rounded p-1 text-red-500 hover:bg-red-50 disabled:opacity-50"
                                  >
                                    <Trash2 className="h-4 w-4" />
                                  </button>
                                </div>
                              </div>
                            </li>
                          ))}
                        </ul>
                      )}
                    </div>

                    {/* Footer */}
                    {!cart?.is_empty && (
                      <div className="border-t px-4 py-4">
                        <div className="flex justify-between text-lg font-semibold">
                          <span>Subtotal</span>
                          <span>{formatPrice(cart?.subtotal || 0)}</span>
                        </div>
                        <p className="mt-1 text-sm text-gray-500">
                          Shipping and taxes calculated at checkout.
                        </p>
                        <Link
                          to="/checkout"
                          onClick={onClose}
                          className="btn-primary btn-lg mt-4 w-full"
                        >
                          Checkout
                        </Link>
                        <Link
                          to="/cart"
                          onClick={onClose}
                          className="btn-outline btn-md mt-2 w-full"
                        >
                          View Cart
                        </Link>
                      </div>
                    )}
                  </div>
                </Dialog.Panel>
              </Transition.Child>
            </div>
          </div>
        </div>
      </Dialog>
    </Transition>
  );
}
