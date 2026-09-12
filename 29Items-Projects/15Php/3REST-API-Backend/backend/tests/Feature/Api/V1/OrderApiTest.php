<?php

namespace Tests\Feature\Api\V1;

use App\Models\Order;
use App\Models\Product;
use App\Models\User;
use App\Models\UserEvent;
use App\Notifications\ProductPurchasedNotification;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Notification;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class OrderApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_places_an_order_decrements_stock_and_records_telemetry(): void
    {
        Notification::fake();

        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $product = Product::factory()->create(['stock' => 10, 'price_cents' => 5000]);

        $this->postJson('/api/v1/orders', [
            'items' => [['product_id' => $product->id, 'quantity' => 2]],
        ])
            ->assertCreated()
            ->assertJsonPath('data.status', 'paid')
            ->assertJsonPath('data.total_cents', 10000) // 2 × $50.00
            ->assertJsonPath('data.items.0.quantity', 2);

        $this->assertDatabaseHas('orders', ['user_id' => $user->id, 'total_cents' => 10000]);
        $this->assertDatabaseHas('order_items', ['product_id' => $product->id, 'quantity' => 2]);

        // Stock decremented.
        $this->assertSame(8, $product->fresh()->stock);

        // The queued listener (sync in tests) recorded purchase telemetry...
        $this->assertDatabaseHas('user_events', [
            'user_id' => $user->id,
            'product_id' => $product->id,
            'type' => UserEvent::TYPE_PURCHASE,
        ]);

        // ...and notified the buyer.
        Notification::assertSentTo($user, ProductPurchasedNotification::class);
    }

    public function test_it_rejects_an_order_that_exceeds_stock_with_409(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $product = Product::factory()->create(['stock' => 1]);

        $this->postJson('/api/v1/orders', [
            'items' => [['product_id' => $product->id, 'quantity' => 5]],
        ])
            ->assertStatus(409)
            ->assertJsonPath('error_code', 'INSUFFICIENT_STOCK');

        // Transaction rolled back: no order, stock untouched.
        $this->assertDatabaseCount('orders', 0);
        $this->assertSame(1, $product->fresh()->stock);
    }

    public function test_order_requires_items(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/orders', [])
            ->assertStatus(422)
            ->assertJsonValidationErrors(['items']);
    }

    public function test_order_rejects_unknown_product(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/orders', [
            'items' => [['product_id' => 999999, 'quantity' => 1]],
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors(['items.0.product_id']);
    }

    public function test_a_user_can_list_their_own_orders(): void
    {
        $user = User::factory()->create();
        Order::factory()->count(3)->create(['user_id' => $user->id]);
        Order::factory()->count(2)->create(); // other users' orders

        Sanctum::actingAs($user);

        $this->getJson('/api/v1/orders')
            ->assertOk()
            ->assertJsonCount(3, 'data');
    }

    public function test_a_user_cannot_view_another_users_order(): void
    {
        $owner = User::factory()->create();
        $other = User::factory()->create();
        $order = Order::factory()->create(['user_id' => $owner->id]);

        Sanctum::actingAs($other);

        $this->getJson("/api/v1/orders/{$order->id}")
            ->assertForbidden(); // 403 via OrderPolicy
    }
}
