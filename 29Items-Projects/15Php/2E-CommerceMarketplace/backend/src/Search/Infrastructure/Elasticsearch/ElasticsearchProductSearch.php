<?php

declare(strict_types=1);

namespace App\Search\Infrastructure\Elasticsearch;

use App\Search\Application\Query\ProductSearchPort;
use Elastic\Elasticsearch\Client;
use Elastic\Elasticsearch\Response\Elasticsearch;
use stdClass;

/** Elasticsearch adapter implementing the product search port. */
final readonly class ElasticsearchProductSearch implements ProductSearchPort
{
    public function __construct(
        private Client $client,
        private string $indexPrefix,
    ) {
    }

    public function search(string $term, int $page, int $perPage): array
    {
        $query = '' === trim($term)
            ? ['match_all' => new stdClass()]
            : ['multi_match' => [
                'query' => $term,
                'fields' => ['name^3', 'description'],
                'fuzziness' => 'AUTO',
            ]];

        $response = $this->client->search([
            'index' => $this->indexPrefix.'_products',
            'body' => [
                'from' => ($page - 1) * $perPage,
                'size' => $perPage,
                'query' => $query,
                // TODO: add facets/aggregations (category, price range, seller).
            ],
        ]);

        // The synchronous client always returns a response (never a Promise).
        \assert($response instanceof Elasticsearch);

        /** @var array{hits: array{total: array{value: int}, hits: list<array{_source: array<string, mixed>}>}} $body */
        $body = $response->asArray();

        return [
            'total' => $body['hits']['total']['value'],
            'items' => array_map(static fn (array $h): array => $h['_source'], $body['hits']['hits']),
        ];
    }
}
