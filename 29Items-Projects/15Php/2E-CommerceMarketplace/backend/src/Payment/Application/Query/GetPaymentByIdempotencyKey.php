<?php

declare(strict_types=1);

namespace App\Payment\Application\Query;

use App\Payment\Application\DTO\PaymentView;
use App\Shared\Domain\Bus\Query\Query;

/**
 * @implements Query<PaymentView|null>
 */
final readonly class GetPaymentByIdempotencyKey implements Query
{
    public function __construct(public string $idempotencyKey)
    {
    }
}
