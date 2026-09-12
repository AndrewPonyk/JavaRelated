<?php

declare(strict_types=1);

namespace App\Catalog\Application\Query;

use App\Catalog\Application\DTO\ProductView;
use App\Shared\Domain\Bus\Query\Query;

/**
 * @implements Query<list<ProductView>>
 */
final readonly class ListProductsBySeller implements Query
{
    public function __construct(
        public string $sellerId,
        public int $page = 1,
        public int $perPage = 20,
    ) {
    }
}
