<?php

declare(strict_types=1);

namespace App\Catalog\Application\Command;

use App\Catalog\Domain\Model\Product;
use App\Catalog\Domain\Model\ProductId;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;
use App\Shared\Domain\Bus\Event\EventBus;
use App\Shared\Domain\ValueObject\Money;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * Handles {@see CreateProduct}. Runs inside the command bus' Doctrine
 * transaction (see messenger.yaml). After persistence, recorded domain events
 * are published so the Search context can index the product.
 */
#[AsMessageHandler(bus: 'command.bus')]
final readonly class CreateProductHandler
{
    public function __construct(
        private ProductRepositoryInterface $products,
        private EventBus $eventBus,
    ) {
    }

    public function __invoke(CreateProduct $command): void
    {
        $product = Product::create(
            ProductId::fromString($command->productId),
            $command->sellerId,
            $command->name,
            $command->description,
            new Money($command->priceMinor, $command->currency),
            $command->stock,
        );

        $this->products->save($product);

        // Transactional outbox semantics: events published after the unit of work.
        $this->eventBus->publish(...$product->pullDomainEvents());
    }
}
