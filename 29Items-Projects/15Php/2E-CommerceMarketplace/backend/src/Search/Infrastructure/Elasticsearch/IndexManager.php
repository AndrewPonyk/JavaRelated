<?php

declare(strict_types=1);

namespace App\Search\Infrastructure\Elasticsearch;

use Elastic\Elasticsearch\Client;
use Elastic\Elasticsearch\Response\Elasticsearch;

/**
 * Owns the product index lifecycle: name resolution and creation with an
 * EXPLICIT mapping. Defining the mapping up front avoids ES "mapping drift" (a
 * field's type being inferred inconsistently); the index is a derived read model
 * and can always be rebuilt from PostgreSQL via the reindex command.
 */
final readonly class IndexManager
{
    public function __construct(
        private Client $client,
        private string $indexPrefix,
    ) {
    }

    public function indexName(): string
    {
        return $this->indexPrefix.'_products';
    }

    /**
     * Create the index with its mapping if it does not already exist.
     *
     * @return bool true if it was created, false if it already existed
     */
    public function ensureIndex(): bool
    {
        $name = $this->indexName();

        $exists = $this->client->indices()->exists(['index' => $name]);
        \assert($exists instanceof Elasticsearch); // synchronous client

        if ($exists->asBool()) {
            return false;
        }

        $this->client->indices()->create([
            'index' => $name,
            'body' => [
                'mappings' => [
                    'properties' => [
                        'id' => ['type' => 'keyword'],
                        'sellerId' => ['type' => 'keyword'],
                        'name' => [
                            'type' => 'text',
                            'fields' => ['keyword' => ['type' => 'keyword', 'ignore_above' => 256]],
                        ],
                        'description' => ['type' => 'text'],
                        'priceMinor' => ['type' => 'long'],
                        'currency' => ['type' => 'keyword'],
                        'active' => ['type' => 'boolean'],
                        'createdAt' => ['type' => 'date'],
                    ],
                ],
            ],
        ]);

        return true;
    }
}
