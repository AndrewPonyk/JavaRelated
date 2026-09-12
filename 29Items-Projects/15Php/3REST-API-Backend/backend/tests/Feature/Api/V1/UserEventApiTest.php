<?php

namespace Tests\Feature\Api\V1;

use App\Models\Product;
use App\Models\User;
use App\Models\UserEvent;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class UserEventApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_records_a_view_signal_with_server_assigned_weight(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $product = Product::factory()->create();

        $this->postJson('/api/v1/events', [
            'product_id' => $product->id,
            'type' => 'view',
        ])
            ->assertCreated()
            ->assertJsonPath('recorded', true);

        $this->assertDatabaseHas('user_events', [
            'user_id' => $user->id,
            'product_id' => $product->id,
            'type' => 'view',
            'weight' => UserEvent::WEIGHTS['view'],
        ]);
    }

    public function test_it_rejects_a_purchase_signal_from_this_endpoint(): void
    {
        // Purchase signals are written only by the order flow — they can't be faked.
        $user = User::factory()->create();
        Sanctum::actingAs($user);
        $product = Product::factory()->create();

        $this->postJson('/api/v1/events', [
            'product_id' => $product->id,
            'type' => 'purchase',
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors(['type']);
    }

    public function test_it_requires_an_existing_product(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/events', [
            'product_id' => 999999,
            'type' => 'view',
        ])
            ->assertStatus(422)
            ->assertJsonValidationErrors(['product_id']);
    }

    public function test_it_requires_authentication(): void
    {
        $product = Product::factory()->create();

        $this->postJson('/api/v1/events', [
            'product_id' => $product->id,
            'type' => 'view',
        ])->assertUnauthorized();
    }
}
