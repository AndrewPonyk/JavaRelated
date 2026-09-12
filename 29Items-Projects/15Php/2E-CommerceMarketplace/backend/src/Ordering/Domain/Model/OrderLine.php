<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Model;

use App\Shared\Domain\ValueObject\Money;
use DomainException;

/** A single line in an order. Immutable value object. */
final readonly class OrderLine
{
    public function __construct(
        public string $productId,
        public string $sellerId,
        public int $quantity,
        public Money $unitPrice,
    ) {
        if ($quantity < 1) {
            throw new DomainException('Order line quantity must be at least 1.');
        }
    }

    public function lineTotal(): Money
    {
        return $this->unitPrice->multiply($this->quantity);
    }
}
