<?php

declare(strict_types=1);

namespace App\Payment\Domain\Model;

use DomainException;

/** Raised when capture is attempted on an order under a fraud hold. Mapped to 409. */
final class PaymentBlockedException extends DomainException
{
    public static function forOrder(string $orderId): self
    {
        return new self(\sprintf('Payment for order "%s" is blocked pending fraud review.', $orderId));
    }
}
