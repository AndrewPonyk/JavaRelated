<?php

namespace Tests\Feature\Services;

use App\Models\Product;
use App\Models\Recommendation;
use App\Models\User;
use App\Models\UserEvent;
use App\Services\Recommendation\RecommendationService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class RecommendationServiceTest extends TestCase
{
    use RefreshDatabase;

    private function service(): RecommendationService
    {
        return app(RecommendationService::class);
    }

    public function test_rebuild_generates_recommendations_aligned_to_user_behavior(): void
    {
        $user = User::factory()->create();
        $electronics = Product::factory()->count(4)->create(['category' => 'electronics']);
        Product::factory()->count(4)->create(['category' => 'books']);

        // User only engages with electronics.
        $electronics->each(fn (Product $p) => UserEvent::factory()->ofType(UserEvent::TYPE_VIEW)->create([
            'user_id' => $user->id,
            'product_id' => $p->id,
        ]));

        $processed = $this->service()->rebuildAll();

        $this->assertGreaterThanOrEqual(1, $processed);
        $this->assertDatabaseHas('recommendations', ['user_id' => $user->id]);

        // All recommended products belong to the engaged category.
        $recommendedCategories = Recommendation::where('user_id', $user->id)
            ->with('product')
            ->get()
            ->pluck('product.category')
            ->unique()
            ->values()
            ->all();

        $this->assertEquals(['electronics'], $recommendedCategories);
    }

    public function test_rebuild_excludes_already_purchased_products(): void
    {
        $user = User::factory()->create();
        $products = Product::factory()->count(3)->create(['category' => 'home']);
        $purchased = $products->first();

        // Views on all, but a purchase on one → purchased one must be excluded.
        $products->each(fn (Product $p) => UserEvent::factory()->ofType(UserEvent::TYPE_VIEW)->create([
            'user_id' => $user->id,
            'product_id' => $p->id,
        ]));
        UserEvent::factory()->ofType(UserEvent::TYPE_PURCHASE)->create([
            'user_id' => $user->id,
            'product_id' => $purchased->id,
        ]);

        $this->service()->rebuildAll();

        $this->assertDatabaseMissing('recommendations', [
            'user_id' => $user->id,
            'product_id' => $purchased->id,
        ]);
    }

    public function test_for_user_returns_popular_fallback_on_cold_start(): void
    {
        $user = User::factory()->create();
        Product::factory()->count(3)->create();

        $recs = $this->service()->forUser($user);

        $this->assertNotEmpty($recs);
        $this->assertTrue($recs->every(fn ($rec) => $rec->cluster === -1));
    }

    public function test_rebuild_returns_zero_when_there_is_no_telemetry(): void
    {
        $this->assertSame(0, $this->service()->rebuildAll());
    }
}
