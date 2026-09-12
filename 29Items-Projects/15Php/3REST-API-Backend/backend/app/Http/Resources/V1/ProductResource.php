<?php

namespace App\Http\Resources\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;
use OpenApi\Attributes as OA;

/**
 * Output boundary: defines exactly which fields leave the system.
 * New DB columns never auto-leak — they must be added here deliberately.
 */
#[OA\Schema(
    schema: 'Product',
    properties: [
        new OA\Property(property: 'id', type: 'integer', example: 42),
        new OA\Property(property: 'name', type: 'string', example: 'Mechanical Keyboard'),
        new OA\Property(property: 'description', type: 'string', nullable: true),
        new OA\Property(property: 'price', type: 'number', format: 'float', example: 129.99),
        new OA\Property(property: 'currency', type: 'string', example: 'USD'),
        new OA\Property(property: 'category', type: 'string', example: 'electronics'),
        new OA\Property(property: 'stock', type: 'integer', example: 17),
        new OA\Property(property: 'is_active', type: 'boolean', example: true),
    ],
)]
class ProductResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'name' => $this->name,
            'description' => $this->description,
            // Present money as a decimal even though it's stored as minor units.
            'price' => round($this->price_cents / 100, 2),
            'price_cents' => $this->price_cents,
            'currency' => $this->currency,
            'category' => $this->category,
            'stock' => $this->stock,
            'is_active' => $this->is_active,
            // Only included when the relation was eager-loaded (avoids N+1).
            'owner' => $this->whenLoaded('owner', fn () => [
                'id' => $this->owner->id,
                'name' => $this->owner->name,
            ]),
            'created_at' => $this->created_at?->toIso8601String(),
            '_links' => [
                'self' => route('api.v1.products.show', $this->id),
            ],
        ];
    }
}
