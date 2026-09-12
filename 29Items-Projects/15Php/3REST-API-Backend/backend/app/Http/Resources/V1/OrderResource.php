<?php

namespace App\Http\Resources\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

class OrderResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'id' => $this->id,
            'status' => $this->status,
            'total' => round($this->total_cents / 100, 2),
            'total_cents' => $this->total_cents,
            'currency' => $this->currency,
            'items' => $this->whenLoaded('items', fn () => $this->items->map(fn ($item) => [
                'product_id' => $item->product_id,
                'product_name' => $item->relationLoaded('product') ? $item->product?->name : null,
                'quantity' => $item->quantity,
                'unit_price' => round($item->unit_price_cents / 100, 2),
                'subtotal' => round(($item->unit_price_cents * $item->quantity) / 100, 2),
            ])),
            'created_at' => $this->created_at?->toIso8601String(),
        ];
    }
}
