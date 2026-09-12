<?php

namespace Tests\Feature\Api\V1;

use App\Models\Product;
use App\Models\User;
use App\Models\UserEvent;
use App\Services\Recommendation\RecommendationService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Laravel\Sanctum\Sanctum;
use Tests\TestCase;

class RecommendationApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_returns_personalized_recommendations_from_behavior(): void
    {
        $user = User::factory()->create();

        // A catalog with a clear category the user engages with.
        $electronics = Product::factory()->count(3)->create(['category' => 'electronics']);
        Product::factory()->count(3)->create(['category' => 'books']);

        // User views electronics → their feature vector favors electronics.
        $electronics->each(fn (Product $p) => UserEvent::factory()->ofType(UserEvent::TYPE_VIEW)->create([
            'user_id' => $user->id,
            'product_id' => $p->id,
        ]));

        app(RecommendationService::class)->rebuildAll();

        Sanctum::actingAs($user);

        $response = $this->getJson('/api/v1/recommendations')
            ->assertOk()
            ->assertJsonStructure([
                'data' => [['product_id', 'score', 'cluster', 'reason', 'product' => ['id', 'name', 'category']]],
            ]);

        // Every recommended product is from the cluster's preferred category.
        $categories = collect($response->json('data'))->pluck('product.category')->unique();
        $this->assertEquals(['electronics'], $categories->values()->all());
        $this->assertSame('behavioral_cluster', $response->json('data.0.reason'));
    }

    public function test_cold_start_user_receives_a_popular_fallback(): void
    {
        $user = User::factory()->create(); // no telemetry, no precomputed recs
        Product::factory()->count(3)->create();

        Sanctum::actingAs($user);

        $this->getJson('/api/v1/recommendations')
            ->assertOk()
            ->assertJsonPath('data.0.reason', 'popular')
            ->assertJsonPath('data.0.cluster', -1);
    }

    public function test_it_respects_the_limit_parameter(): void
    {
        $user = User::factory()->create();
        Product::factory()->count(10)->create();

        Sanctum::actingAs($user);

        $this->getJson('/api/v1/recommendations?limit=3')
            ->assertOk()
            ->assertJsonCount(3, 'data');
    }

    public function test_it_requires_authentication(): void
    {
        $this->getJson('/api/v1/recommendations')->assertUnauthorized();
    }
}
