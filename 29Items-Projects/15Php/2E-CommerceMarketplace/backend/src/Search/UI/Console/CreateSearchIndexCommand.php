<?php

declare(strict_types=1);

namespace App\Search\UI\Console;

use App\Search\Infrastructure\Elasticsearch\IndexManager;
use Symfony\Component\Console\Attribute\AsCommand;
use Symfony\Component\Console\Command\Command;
use Symfony\Component\Console\Input\InputInterface;
use Symfony\Component\Console\Output\OutputInterface;
use Symfony\Component\Console\Style\SymfonyStyle;

/**
 * Creates the product search index with its explicit mapping (idempotent).
 * Invoked by `make backend-install`; safe to run repeatedly.
 */
#[AsCommand(
    name: 'app:search:create-index',
    description: 'Create the Elasticsearch product index (with mapping) if it does not exist.',
)]
final class CreateSearchIndexCommand extends Command
{
    public function __construct(private readonly IndexManager $indexManager)
    {
        parent::__construct();
    }

    protected function execute(InputInterface $input, OutputInterface $output): int
    {
        $io = new SymfonyStyle($input, $output);

        $created = $this->indexManager->ensureIndex();

        if ($created) {
            $io->success(\sprintf('Created index "%s".', $this->indexManager->indexName()));
        } else {
            $io->note(\sprintf('Index "%s" already exists.', $this->indexManager->indexName()));
        }

        return Command::SUCCESS;
    }
}
