<?php

declare(strict_types=1);

namespace App\Search\Infrastructure\Elasticsearch;

use App\Catalog\Domain\Event\ProductPriceChanged;
use Elastic\Elasticsearch\Client;
use Elastic\Elasticsearch\Exception\ClientResponseException;
use Psr\Log\LoggerInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * Projects {@see ProductPriceChanged} onto the search read model. If the document
 * is not yet indexed (event ordering race vs ProductCreated), we log and move on —
 * the create projection or a reindex will reconcile it. Eventual consistency on
 * the read side is an explicit, accepted tradeoff (see ARCHITECTURE §2.3).
 */
#[AsMessageHandler(bus: 'event.bus')]
final readonly class ProductPriceProjector
{
    public function __construct(
        private Client $client,
        private IndexManager $indexManager,
        private LoggerInterface $logger,
    ) {
    }

    public function __invoke(ProductPriceChanged $event): void
    {
        try {
            $this->client->update([
                'index' => $this->indexManager->indexName(),
                'id' => $event->productId,
                'retry_on_conflict' => 3,
                'body' => [
                    'doc' => [
                        'priceMinor' => $event->newPriceMinor,
                        'currency' => $event->currency,
                    ],
                ],
            ]);
        } catch (ClientResponseException $e) {
            if (404 !== $e->getResponse()->getStatusCode()) {
                throw $e;
            }
            $this->logger->warning('Price change for not-yet-indexed product; skipping.', [
                'productId' => $event->productId,
            ]);
        }
    }
}
