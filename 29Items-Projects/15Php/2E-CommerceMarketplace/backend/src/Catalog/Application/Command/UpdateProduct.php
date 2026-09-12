<?php

declare(strict_types=1);

namespace App\Catalog\Application\Command;

use App\Shared\Domain\Bus\Command\Command;

/**
 * Partial update of a product. Null fields are left unchanged. `sellerId` is the
 * authenticated actor; the handler verifies they own the product (ownership is an
 * application invariant, enforced server-side — never trusted from the client).
 */
final readonly class UpdateProduct implements Command
{
    public function __construct(
        public string $productId,
        public string $sellerId,
        public ?string $name = null,
        public ?string $description = null,
        public ?int $priceMinor = null,
        public ?string $currency = null,
        public ?int $stock = null,
        public ?bool $active = null,
    ) {
    }
}
