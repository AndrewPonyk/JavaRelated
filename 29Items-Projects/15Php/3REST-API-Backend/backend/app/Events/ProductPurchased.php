<?php

namespace App\Events;

use App\Models\Product;
use App\Models\User;
use Illuminate\Broadcasting\InteractsWithSockets;
use Illuminate\Foundation\Events\Dispatchable;
use Illuminate\Queue\SerializesModels;

/**
 * Domain event: a buyer purchased a product. Emitted by the order/checkout
 * flow. Decouples "what happened" from "what to do about it" — listeners
 * handle notifications and recommendation updates asynchronously.
 */
class ProductPurchased
{
    use Dispatchable, InteractsWithSockets, SerializesModels;

    public function __construct(
        public readonly User $buyer,
        public readonly Product $product,
        public readonly int $quantity = 1,
    ) {}
}
