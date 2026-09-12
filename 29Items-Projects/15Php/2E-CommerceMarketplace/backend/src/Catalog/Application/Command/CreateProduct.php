<?php

declare(strict_types=1);

namespace App\Catalog\Application\Command;

use App\Shared\Domain\Bus\Command\Command;

/** Intent to create a product. Immutable message carried on the command bus. */
final readonly class CreateProduct implements Command
{
    public function __construct(
        public string $productId,
        public string $sellerId,
        public string $name,
        public string $description,
        public int $priceMinor,
        public string $currency,
        public int $stock,
    ) {
    }
}
