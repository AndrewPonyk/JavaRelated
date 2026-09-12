<?php

namespace Database\Factories;

use App\Models\Product;
use App\Models\User;
use App\Models\UserEvent;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<UserEvent>
 */
class UserEventFactory extends Factory
{
    protected $model = UserEvent::class;

    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $type = fake()->randomElement(array_keys(UserEvent::WEIGHTS));

        return [
            'user_id' => User::factory(),
            'product_id' => Product::factory(),
            'type' => $type,
            'weight' => UserEvent::WEIGHTS[$type],
            'context' => null,
        ];
    }

    public function ofType(string $type): static
    {
        return $this->state(fn () => [
            'type' => $type,
            'weight' => UserEvent::WEIGHTS[$type] ?? 1.0,
        ]);
    }
}
