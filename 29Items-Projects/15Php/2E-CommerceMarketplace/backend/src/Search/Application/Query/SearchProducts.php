<?php

declare(strict_types=1);

namespace App\Search\Application\Query;

use App\Shared\Domain\Bus\Query\Query;

/**
 * @implements Query<array{total: int, items: list<array<string, mixed>>}>
 */
final readonly class SearchProducts implements Query
{
    public function __construct(
        public string $term,
        public int $page = 1,
        public int $perPage = 20,
    ) {
    }
}
