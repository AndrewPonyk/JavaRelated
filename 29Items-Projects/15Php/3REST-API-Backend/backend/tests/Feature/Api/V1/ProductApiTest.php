<?php

namespace Tests\Feature\Api\V1;

use App\Models\Product;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

/**
 * HTTP-level contract tests for the v1 Products API: auth gating, CRUD,
 * validation, ownership, and rate limiting.
 */
class ProductApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_unauthenticated_requests_are_rejected(): void
    {
        $this->getJson('/api/v1/products')
            ->assertUnauthorized(); // 401
    }

    public function test_it_lists_products_with_pagination_meta(): void
    {
        Sanctum::actingAs(User::factory()->create());
        Product::factory()->count(3)->create();

        $this->getJson('/api/v1/products')
            ->assertOk()
            ->assertJsonStructure([
                'data' => [['id', 'name', 'price', 'currency', 'category']],
                'meta' => ['api_version', 'total', 'per_page', 'current_page'],
                'links' => ['first', 'next', 'prev'],
            ])
            ->assertJsonPath('meta.api_version', 'v1');
    }

    public function test_it_filters_products_by_category(): void
    {
        Sanctum::actingAs(User::factory()->create());
        Product::factory()->create(['category' => 'electronics', 'name' => 'Keyboard']);
        Product::factory()->create(['category' => 'books', 'name' => 'Novel']);

        $this->getJson('/api/v1/products?category=electronics')
            ->assertOk()
            ->assertJsonCount(1, 'data')
            ->assertJsonPath('data.0.category', 'electronics');
    }

    public function test_it_creates_a_product_for_the_authenticated_owner(): void
    {
        $user = User::factory()->create();
        Sanctum::actingAs($user);

        $payload = [
            'name' => 'Mechanical Keyboard',
            'description' => 'Hot-swappable, RGB.',
            'price_cents' => 12_999,
            'currency' => 'usd', // normalized to USD by the Form Request
            'category' => 'electronics',
            'stock' => 17,
        ];

        $this->postJson('/api/v1/products', $payload)
            ->assertCreated() // 201
            ->assertJsonPath('data.name', 'Mechanical Keyboard')
            ->assertJsonPath('data.currency', 'USD')
            ->assertJsonPath('data.price', 129.99);

        $this->assertDatabaseHas('products', [
            'name' => 'Mechanical Keyboard',
            'user_id' => $user->id,
        ]);
    }

    public function test_it_rejects_invalid_input_with_422(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->postJson('/api/v1/products', ['name' => ''])
            ->assertStatus(422)
            ->assertJsonValidationErrors(['name', 'price_cents', 'category']);
    }

    public function test_it_shows_a_single_product(): void
    {
        Sanctum::actingAs(User::factory()->create());
        $product = Product::factory()->create(['name' => 'Widget']);

        $this->getJson("/api/v1/products/{$product->id}")
            ->assertOk()
            ->assertJsonPath('data.id', $product->id)
            ->assertJsonPath('data.name', 'Widget')
            ->assertJsonPath('data.owner.id', $product->user_id);
    }

    public function test_it_returns_404_for_a_missing_product(): void
    {
        Sanctum::actingAs(User::factory()->create());

        $this->getJson('/api/v1/products/999999')
            ->assertNotFound()
            ->assertJsonPath('error_code', 'RESOURCE_NOT_FOUND');
    }

    public function test_the_owner_can_update_their_product(): void
    {
        $owner = User::factory()->create();
        $product = Product::factory()->create(['user_id' => $owner->id, 'price_cents' => 1000]);

        Sanctum::actingAs($owner);

        $this->putJson("/api/v1/products/{$product->id}", ['price_cents' => 2500])
            ->assertOk()
            ->assertJsonPath('data.price_cents', 2500);

        $this->assertDatabaseHas('products', ['id' => $product->id, 'price_cents' => 2500]);
    }

    public function test_a_user_cannot_update_a_product_they_do_not_own(): void
    {
        $owner = User::factory()->create();
        $intruder = User::factory()->create();
        $product = Product::factory()->create(['user_id' => $owner->id]);

        Sanctum::actingAs($intruder);

        $this->putJson("/api/v1/products/{$product->id}", ['name' => 'Hijacked'])
            ->assertForbidden(); // 403 via UpdateProductRequest::authorize()
    }

    public function test_the_owner_can_delete_their_product(): void
    {
        $owner = User::factory()->create();
        $product = Product::factory()->create(['user_id' => $owner->id]);

        Sanctum::actingAs($owner);

        $this->deleteJson("/api/v1/products/{$product->id}")
            ->assertNoContent(); // 204

        $this->assertSoftDeleted('products', ['id' => $product->id]);
    }

    public function test_a_non_owner_cannot_delete_a_product(): void
    {
        $owner = User::factory()->create();
        $intruder = User::factory()->create();
        $product = Product::factory()->create(['user_id' => $owner->id]);

        Sanctum::actingAs($intruder);

        $this->deleteJson("/api/v1/products/{$product->id}")
            ->assertForbidden(); // 403 via ProductPolicy
    }

    public function test_auth_endpoints_are_rate_limited(): void
    {
        // The 'auth' limiter allows API_AUTH_RATE_LIMIT (default 5) per minute.
        for ($i = 0; $i < 5; $i++) {
            $this->postJson('/api/v1/auth/login', [
                'email' => 'nobody@example.com',
                'password' => 'wrong-password',
            ]);
        }

        $this->postJson('/api/v1/auth/login', [
            'email' => 'nobody@example.com',
            'password' => 'wrong-password',
        ])->assertStatus(429); // Too Many Requests
    }
}
