<?php

declare(strict_types=1);

namespace App\FraudDetection\Application\Command;

use App\Shared\Domain\Bus\Command\Command;

/** A reviewer's decision on a flagged order. `cleared` = legitimate (lift holds). */
final readonly class ResolveFraudReview implements Command
{
    public function __construct(
        public string $orderId,
        public bool $cleared,
    ) {
    }
}
