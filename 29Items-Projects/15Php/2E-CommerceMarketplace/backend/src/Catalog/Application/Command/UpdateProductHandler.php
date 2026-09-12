<?php

declare(strict_types=1);

namespace App\Catalog\Application\Command;

use App\Catalog\Domain\Model\ProductId;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;
use App\Shared\Domain\Bus\Event\EventBus;
use App\Shared\Domain\Security\AccessDeniedException;
use App\Shared\Domain\ValueObject\Money;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

/**
 * Applies a partial product update after verifying ownership. A price change
 * records {@see \App\Catalog\Domain\Event\ProductPriceChanged}, which the Search
 * projector consumes to keep the read model's price current.
 */
#[AsMessageHandler(bus: 'command.bus')]
final readonly class UpdateProductHandler
{
    public function __construct(
        private ProductRepositoryInterface $products,
        private EventBus $eventBus,
    ) {
    }

    public function __invoke(UpdateProduct $command): void
    {
        $product = $this->products->get(ProductId::fromString($command->productId));

        if ($product->sellerId() !== $command->sellerId) {
            throw AccessDeniedException::notOwner('product');
        }

        if (null !== $command->name) {
            $product->rename($command->name);
        }
        if (null !== $command->description) {
            $product->describe($command->description);
        }
        if (null !== $command->priceMinor) {
            $currency = strtoupper($command->currency ?? $product->price()->currency);
            $product->changePrice(new Money($command->priceMinor, $currency));
        }
        if (null !== $command->stock) {
            $product->changeStock($command->stock);
        }
        if (null !== $command->active) {
            $command->active ? $product->activate() : $product->deactivate();
        }

        // The aggregate is already managed; doctrine_transaction commits the unit
        // of work. Publish any recorded events (e.g. ProductPriceChanged) after.
        $this->eventBus->publish(...$product->pullDomainEvents());
    }
}
