<?php

declare(strict_types=1);

namespace App\FraudDetection\Domain\Event;

use App\Shared\Domain\Event\DomainEvent;
use DateTimeImmutable;

/**
 * Emitted when a human resolves a flagged order. When `cleared` is true the order
 * is deemed legitimate; the Payment context subscribes to lift any hold.
 */
final readonly class FraudReviewResolved implements DomainEvent
{
    public function __construct(
        public string $orderId,
        public bool $cleared,
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
        return 'fraud.review_resolved';
    }
}
