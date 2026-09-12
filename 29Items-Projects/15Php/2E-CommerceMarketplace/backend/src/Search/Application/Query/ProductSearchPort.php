<?php

declare(strict_types=1);

namespace App\Search\Application\Query;

/**
 * Port for querying the product read model. Keeps the application/query layer
 * independent of Elasticsearch specifics.
 */
interface ProductSearchPort
{
    /**
     * @return array{total: int, items: list<array<string, mixed>>}
     */
    public function search(string $term, int $page, int $perPage): array;
}
