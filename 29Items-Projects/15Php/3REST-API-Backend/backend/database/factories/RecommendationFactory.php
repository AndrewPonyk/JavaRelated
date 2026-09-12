<?php

namespace Database\Factories;

use App\Models\Product;
use App\Models\Recommendation;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Recommendation>
 */
class RecommendationFactory extends Factory
{
    protected $model = Recommendation::class;

    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'product_id' => Product::factory(),
            'cluster' => fake()->numberBetween(0, 7),
            'score' => fake()->randomFloat(4, 0, 10),
            'computed_at' => now(),
        ];
    }
}
