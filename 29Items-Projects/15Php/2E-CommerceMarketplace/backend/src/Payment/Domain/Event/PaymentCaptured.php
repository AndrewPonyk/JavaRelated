<?php

declare(strict_types=1);

namespace App\Payment\Domain\Event;

use App\Shared\Domain\Event\DomainEvent;
use DateTimeImmutable;

/**
 * Emitted after funds are successfully captured. The Ordering context subscribes
 * to transition the order to PAID (cross-context integration via events only).
 */
final readonly class PaymentCaptured implements DomainEvent
{
    public function __construct(
        public string $orderId,
        public string $transactionId,
        public int $amountMinor,
        public string $currency,
        public string $pspReference,
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
        return 'payment.captured';
    }
}
