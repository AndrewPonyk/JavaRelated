<?php

declare(strict_types=1);

namespace App\Search\Infrastructure\Elasticsearch;

use App\Catalog\Domain\Event\ProductCreated;
use Elastic\Elasticsearch\Client;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * CQRS projector: keeps the Elasticsearch product read model in sync with the
 * Catalog write model by reacting to Catalog domain events (delivered async via
 * RabbitMQ). This is the ONLY place that writes the search index — the index is
 * a derived, rebuildable view, never a source of truth.
 *
 * Indexing by document id is idempotent, so at-least-once delivery is safe. Price
 * changes are projected by {@see ProductPriceProjector}; a full rebuild is
 * available via `app:search:reindex`.
 */
#[AsMessageHandler(bus: 'event.bus')]
final readonly class ProductIndexer
{
    public function __construct(
        private Client $client,
        private IndexManager $indexManager,
    ) {
    }

    public function __invoke(ProductCreated $event): void
    {
        $this->client->index([
            'index' => $this->indexManager->indexName(),
            'id' => $event->productId,
            'body' => [
                'id' => $event->productId,
                'sellerId' => $event->sellerId,
                'name' => $event->name,
                'priceMinor' => $event->priceMinor,
                'currency' => $event->currency,
                'active' => true,
                'createdAt' => $event->occurredOn->format(\DATE_ATOM),
            ],
        ]);
    }
}
