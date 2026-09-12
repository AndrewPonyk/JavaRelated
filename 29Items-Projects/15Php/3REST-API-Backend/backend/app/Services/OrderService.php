<?php

namespace App\Services;

use App\Events\ProductPurchased;
use App\Exceptions\InsufficientStockException;
use App\Models\Order;
use App\Models\Product;
use App\Models\User;
use Illuminate\Support\Facades\DB;

/**
 * Checkout / order placement. Wraps the whole operation in a DB transaction
 * with row locking so concurrent buyers can't oversell stock, then emits a
 * ProductPurchased domain event per line item (docs/ARCHITECTURE.md §2.3.2).
 */
class OrderService
{
    /**
     * @param  array<int, array{product_id: int, quantity: int}>  $lines
     *
     * @throws InsufficientStockException
     */
    public function place(User $buyer, array $lines): Order
    {
        /** @var array{0: Order, 1: array<int, array{0: Product, 1: int}>} $result */
        $result = DB::transaction(function () use ($buyer, $lines) {
            // Collapse duplicate product lines into a single quantity.
            $quantities = [];
            foreach ($lines as $line) {
                $quantities[$line['product_id']] = ($quantities[$line['product_id']] ?? 0) + (int) $line['quantity'];
            }

            // Lock the rows for the duration of the transaction (prevents oversell).
            $products = Product::query()
                ->whereIn('id', array_keys($quantities))
                ->lockForUpdate()
                ->get()
                ->keyBy('id');

            $order = new Order([
                'user_id' => $buyer->id,
                'currency' => $products->first()?->currency ?? 'USD',
                'status' => Order::STATUS_PAID,
                'total_cents' => 0,
            ]);
            $order->save();

            $total = 0;
            $purchased = [];

            foreach ($quantities as $productId => $quantity) {
                /** @var Product|null $product */
                $product = $products->get($productId);

                if ($product === null || ! $product->is_active) {
                    throw new \RuntimeException("Product {$productId} is not available.");
                }

                if ($product->stock < $quantity) {
                    // Aborts and rolls back the whole transaction.
                    throw InsufficientStockException::for($productId, $quantity, $product->stock);
                }

                $order->items()->create([
                    'product_id' => $product->id,
                    'quantity' => $quantity,
                    'unit_price_cents' => $product->price_cents,
                ]);

                $product->decrement('stock', $quantity);
                $total += $product->price_cents * $quantity;
                $purchased[] = [$product, $quantity];
            }

            $order->update(['total_cents' => $total]);

            return [$order, $purchased];
        });

        [$order, $purchased] = $result;

        // Emit events AFTER commit so queued listeners never observe
        // uncommitted state (docs/ARCHITECTURE.md §2.3.2).
        foreach ($purchased as [$product, $quantity]) {
            ProductPurchased::dispatch($buyer, $product, $quantity);
        }

        return $order->load('items.product');
    }
}
