<?php

declare(strict_types=1);

namespace App\FraudDetection\Domain\Model;

use App\Shared\Domain\Exception\NotFoundException;

/** Raised when resolving a review for an order that has no assessment. Mapped to 404. */
final class FraudAssessmentNotFoundException extends NotFoundException
{
    public static function forOrder(string $orderId): self
    {
        return new self(\sprintf('No fraud assessment found for order "%s".', $orderId));
    }
}
