<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Event;

use App\Shared\Domain\Event\DomainEvent;
use DateTimeImmutable;

/**
 * The first event of every order stream. Carries only serialisable primitives so
 * it can be persisted to the event store and replayed verbatim. Downstream
 * contexts (FraudDetection, Vendor commission, notifications) subscribe to it.
 *
 * @phpstan-type LineData array{productId: string, sellerId: string, quantity: int, unitPriceMinor: int}
 */
final readonly class OrderPlaced implements DomainEvent
{
    /**
     * @param list<LineData> $lines
     */
    public function __construct(
        public string $orderId,
        public string $customerId,
        public string $currency,
        public array $lines,
        public int $totalMinor,
        public DateTimeImmutable $occurredOn,
    ) {
    }

    public function aggregateId(): string
    {
        return $this->orderId;
    }

    public function occurredOn(): DateTimeImmutable
    {
        return $this->occurredOn;
    }

    public static function eventName(): string
    {
        return 'ordering.order_placed';
    }
}
