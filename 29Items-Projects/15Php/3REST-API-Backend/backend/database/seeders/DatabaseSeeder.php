<?php

namespace Database\Seeders;

use App\Models\Product;
use App\Models\User;
use App\Models\UserEvent;
use App\Services\Recommendation\RecommendationService;
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // Deterministic admin for local logins.
        $admin = User::factory()->admin()->create([
            'name' => 'Admin',
            'email' => 'admin@example.com',
            'password' => Hash::make('password'),
        ]);

        // A handful of sellers, each with a catalog.
        $sellers = User::factory()
            ->count(10)
            ->create();

        $sellers->each(fn (User $seller) => Product::factory()->count(8)->create([
            'user_id' => $seller->id,
        ]));

        // Give the admin a few listings too.
        Product::factory()->count(5)->create(['user_id' => $admin->id]);

        // Demo customers with behavioral telemetry so recommendations exist.
        $products = Product::query()->get();
        User::factory()->count(20)->create()->each(function (User $customer) use ($products) {
            // Each customer views a random slice of the catalog, skewed by category.
            $sample = $products->random(min(12, $products->count()));
            foreach ($sample as $product) {
                UserEvent::factory()->ofType(UserEvent::TYPE_VIEW)->create([
                    'user_id' => $customer->id,
                    'product_id' => $product->id,
                ]);
            }
        });

        // Build the first recommendation set from the seeded telemetry.
        app(RecommendationService::class)->rebuildAll();
    }
}
