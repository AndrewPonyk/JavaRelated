<?php

namespace Tests\Feature\Services;

use App\Events\ProductPurchased;
use App\Exceptions\InsufficientStockException;
use App\Models\Product;
use App\Models\User;
use App\Services\OrderService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Event;
use Tests\TestCase;

class OrderServiceTest extends TestCase
{
    use RefreshDatabase;

    private function service(): OrderService
    {
        return app(OrderService::class);
    }

    public function test_it_creates_an_order_decrements_stock_and_totals_correctly(): void
    {
        Event::fake([ProductPurchased::class]);

        $buyer = User::factory()->create();
        $a = Product::factory()->create(['stock' => 10, 'price_cents' => 2500]);
        $b = Product::factory()->create(['stock' => 5, 'price_cents' => 1000]);

        $order = $this->service()->place($buyer, [
            ['product_id' => $a->id, 'quantity' => 3],
            ['product_id' => $b->id, 'quantity' => 2],
        ]);

        // 3×2500 + 2×1000 = 9500
        $this->assertSame(9500, $order->total_cents);
        $this->assertCount(2, $order->items);
        $this->assertSame(7, $a->fresh()->stock);
        $this->assertSame(3, $b->fresh()->stock);

        Event::assertDispatched(ProductPurchased::class, 2); // one per line item
    }

    public function test_it_collapses_duplicate_product_lines(): void
    {
        Event::fake([ProductPurchased::class]);

        $buyer = User::factory()->create();
        $product = Product::factory()->create(['stock' => 10, 'price_cents' => 1000]);

        $order = $this->service()->place($buyer, [
            ['product_id' => $product->id, 'quantity' => 2],
            ['product_id' => $product->id, 'quantity' => 3],
        ]);

        $this->assertCount(1, $order->items);
        $this->assertSame(5, $order->items->first()->quantity);
        $this->assertSame(5, $product->fresh()->stock);
    }

    public function test_it_throws_and_rolls_back_on_insufficient_stock(): void
    {
        $buyer = User::factory()->create();
        $product = Product::factory()->create(['stock' => 2]);

        try {
            $this->service()->place($buyer, [
                ['product_id' => $product->id, 'quantity' => 5],
            ]);
            $this->fail('Expected InsufficientStockException was not thrown.');
        } catch (InsufficientStockException $e) {
            // expected
        }

        // Nothing persisted, stock unchanged.
        $this->assertDatabaseCount('orders', 0);
        $this->assertDatabaseCount('order_items', 0);
        $this->assertSame(2, $product->fresh()->stock);
    }
}
