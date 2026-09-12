<?php

declare(strict_types=1);

namespace App\Ordering\Application\Command;

use App\Shared\Domain\Bus\Command\Command;

/**
 * @phpstan-type LineInput array{productId: string, sellerId: string, quantity: int, unitPriceMinor: int}
 */
final readonly class PlaceOrder implements Command
{
    /**
     * @param list<LineInput> $lines
     */
    public function __construct(
        public string $orderId,
        public string $customerId,
        public string $currency,
        public array $lines,
    ) {
    }
}
