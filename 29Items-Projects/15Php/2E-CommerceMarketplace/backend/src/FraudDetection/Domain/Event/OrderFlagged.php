<?php

declare(strict_types=1);

namespace App\FraudDetection\Domain\Event;

use App\FraudDetection\Domain\Model\FraudDecision;
use App\Shared\Domain\Event\DomainEvent;
use DateTimeImmutable;

/**
 * Emitted when risk scoring yields a non-ALLOW decision. The Payment context
 * subscribes to BLOCK to hold capture; the review queue surfaces REVIEW/BLOCK to
 * human reviewers.
 */
final readonly class OrderFlagged implements DomainEvent
{
    public function __construct(
        public string $orderId,
        public FraudDecision $decision,
        public float $riskScore,
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
        return 'fraud.order_flagged';
    }
}
