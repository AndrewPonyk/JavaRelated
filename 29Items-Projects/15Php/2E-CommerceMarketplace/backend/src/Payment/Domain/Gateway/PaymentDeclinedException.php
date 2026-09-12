<?php

declare(strict_types=1);

namespace App\Payment\Domain\Gateway;

use DomainException;

/** Raised by a PaymentGateway when the PSP declines the charge. Mapped to 409. */
final class PaymentDeclinedException extends DomainException
{
    public static function withReason(string $reason): self
    {
        return new self('Payment declined: '.$reason);
    }
}
