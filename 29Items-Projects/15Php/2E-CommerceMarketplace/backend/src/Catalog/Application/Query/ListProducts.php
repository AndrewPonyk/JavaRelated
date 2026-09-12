<?php

declare(strict_types=1);

namespace App\Catalog\Application\Query;

use App\Catalog\Application\DTO\ProductView;
use App\Shared\Domain\Bus\Query\Query;

/**
 * Public catalog listing of active products (paginated).
 *
 * @implements Query<list<ProductView>>
 */
final readonly class ListProducts implements Query
{
    public function __construct(
        public int $page = 1,
        public int $perPage = 20,
    ) {
    }
}
