<?php

declare(strict_types=1);

namespace App\Payment\Application\Command;

use App\Shared\Domain\Bus\Command\Command;

/**
 * Capture payment for an order. `idempotencyKey` makes client/network retries
 * safe — the same key must never capture twice.
 */
final readonly class CapturePayment implements Command
{
    public function __construct(
        public string $orderId,
        public string $customerId,
        public int $amountMinor,
        public string $currency,
        public string $paymentMethodToken,
        public string $idempotencyKey,
    ) {
    }
}
