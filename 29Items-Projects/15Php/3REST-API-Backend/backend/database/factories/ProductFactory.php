<?php

namespace Database\Factories;

use App\Models\Product;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Product>
 */
class ProductFactory extends Factory
{
    protected $model = Product::class;

    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'name' => fake()->unique()->words(3, true),
            'description' => fake()->sentence(12),
            'price_cents' => fake()->numberBetween(500, 500_00),
            'currency' => 'USD',
            'category' => fake()->randomElement([
                'electronics', 'books', 'home', 'fashion', 'toys', 'sports',
            ]),
            'stock' => fake()->numberBetween(0, 250),
            'is_active' => true,
        ];
    }

    /** State: an out-of-stock, inactive listing. */
    public function inactive(): static
    {
        return $this->state(fn () => ['is_active' => false, 'stock' => 0]);
    }
}
