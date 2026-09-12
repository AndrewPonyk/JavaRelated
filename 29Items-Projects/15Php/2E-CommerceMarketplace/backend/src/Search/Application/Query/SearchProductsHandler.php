<?php

declare(strict_types=1);

namespace App\Search\Application\Query;

use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'query.bus')]
final readonly class SearchProductsHandler
{
    public function __construct(private ProductSearchPort $search)
    {
    }

    /**
     * @return array{total: int, items: list<array<string, mixed>>}
     */
    public function __invoke(SearchProducts $query): array
    {
        return $this->search->search($query->term, max(1, $query->page), min(100, $query->perPage));
    }
}
