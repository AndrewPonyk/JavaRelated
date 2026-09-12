<?php

declare(strict_types=1);

namespace App\Catalog\Domain\Event;

use App\Shared\Domain\Event\DomainEvent;
use DateTimeImmutable;

final readonly class ProductPriceChanged implements DomainEvent
{
    public function __construct(
        public string $productId,
        public int $oldPriceMinor,
        public int $newPriceMinor,
        public string $currency,
        public DateTimeImmutable $occurredOn,
    ) {
    }

    public function aggregateId(): string
    {
        return $this->productId;
    }

    public function occurredOn(): DateTimeImmutable
    {
        return $this->occurredOn;
    }

    public static function eventName(): string
    {
        return 'catalog.product_price_changed';
    }
}
