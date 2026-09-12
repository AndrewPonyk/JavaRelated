<?php

declare(strict_types=1);

namespace App\Ordering\Application\Query;

use App\Ordering\Application\DTO\OrderView;
use App\Shared\Domain\Bus\Query\Query;

/**
 * @implements Query<OrderView>
 */
final readonly class GetOrder implements Query
{
    public function __construct(public string $orderId)
    {
    }
}
