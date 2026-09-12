<?php

declare(strict_types=1);

namespace App\Search\UI\Console;

use App\Catalog\Application\Query\ListAllProducts;
use App\Search\Infrastructure\Elasticsearch\IndexManager;
use App\Shared\Domain\Bus\Query\QueryBus;
use Elastic\Elasticsearch\Client;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Rebuilds the product search read model from PostgreSQL (the source of truth).
 * Use after a mapping change or to recover from index loss — the index is always
 * derivable from the write model (see TECH-NOTES §3.6, "Elasticsearch").
 */
#[AsCommand(
    name: 'app:search:reindex',
    description: 'Reindex all products from the catalog into Elasticsearch.',
)]
final class ReindexProductsCommand extends Command
{
    public function __construct(
        private readonly IndexManager $indexManager,
        private readonly QueryBus $queryBus,
        private readonly Client $client,
    ) {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);
        $this->indexManager->ensureIndex();
        $index = $this->indexManager->indexName();

        $products = $this->queryBus->ask(new ListAllProducts());
        $count = 0;

        foreach ($products as $product) {
            $this->client->index([
                'index' => $index,
                'id' => $product->id,
                'body' => [
                    'id' => $product->id,
                    'sellerId' => $product->sellerId,
                    'name' => $product->name,
                    'description' => $product->description,
                    'priceMinor' => $product->priceMinor,
                    'currency' => $product->currency,
                    'active' => $product->active,
                ],
            ]);
            ++$count;
        }

        $this->client->indices()->refresh(['index' => $index]);
        $io->success(\sprintf('Reindexed %d product(s) into "%s".', $count, $index));

        return Command::SUCCESS;
    }
}
