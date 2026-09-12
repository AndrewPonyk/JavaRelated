<?php

declare(strict_types=1);

namespace App\Catalog\Domain\Event;

use App\Shared\Domain\Event\DomainEvent;
use DateTimeImmutable;

/**
 * Emitted when a product is created. The Search context subscribes to this to
 * index the product in Elasticsearch (CQRS read-model projection).
 */
final readonly class ProductCreated implements DomainEvent
{
    public function __construct(
        public string $productId,
        public string $sellerId,
        public string $name,
        public int $priceMinor,
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
        return 'catalog.product_created';
    }
}
