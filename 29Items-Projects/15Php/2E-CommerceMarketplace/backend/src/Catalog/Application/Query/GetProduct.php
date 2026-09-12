<?php

declare(strict_types=1);

namespace App\Catalog\Application\Query;

use App\Catalog\Application\DTO\ProductView;
use App\Shared\Domain\Bus\Query\Query;

/**
 * @implements Query<ProductView>
 */
final readonly class GetProduct implements Query
{
    public function __construct(public string $productId)
    {
    }
}
