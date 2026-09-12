<?php

namespace Tests\Feature\Console;

use App\Models\Product;
use App\Models\User;
use App\Models\UserEvent;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class RecomputeRecommendationsCommandTest extends TestCase
{
    use RefreshDatabase;

    public function test_it_rebuilds_recommendations_from_telemetry(): void
    {
        $user = User::factory()->create();
        $products = Product::factory()->count(3)->create(['category' => 'electronics']);
        $products->each(fn (Product $p) => UserEvent::factory()->ofType(UserEvent::TYPE_VIEW)->create([
            'user_id' => $user->id,
            'product_id' => $p->id,
        ]));

        $this->artisan('recommendations:recompute')
            ->assertExitCode(0);

        $this->assertDatabaseHas('recommendations', ['user_id' => $user->id]);
    }

    public function test_it_runs_cleanly_with_no_telemetry(): void
    {
        $this->artisan('recommendations:recompute')
            ->assertExitCode(0);

        $this->assertDatabaseCount('recommendations', 0);
    }
}
