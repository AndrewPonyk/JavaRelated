<?php

declare(strict_types=1);

namespace App\Catalog\Application\Query;

use App\Catalog\Application\DTO\ProductView;
use App\Shared\Domain\Bus\Query\Query;

/**
 * All products (the write-side source of truth). Used by the search reindex job
 * to rebuild the Elasticsearch read model. Not exposed over HTTP.
 *
 * @implements Query<list<ProductView>>
 */
final readonly class ListAllProducts implements Query
{
}
