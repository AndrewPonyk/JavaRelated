<?php

declare(strict_types=1);

namespace App\Tests\Unit\Catalog;

use App\Catalog\Application\Command\UpdateProduct;
use App\Catalog\Application\Command\UpdateProductHandler;
use App\Catalog\Domain\Event\ProductPriceChanged;
use App\Catalog\Domain\Model\Product;
use App\Catalog\Domain\Model\ProductId;
use App\Shared\Domain\Security\AccessDeniedException;
use App\Shared\Domain\ValueObject\Money;
use App\Tests\Support\InMemoryProductRepository;
use App\Tests\Support\RecordingEventBus;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;
use Symfony\Component\Uid\Uuid;

final class UpdateProductHandlerTest extends TestCase
{
    private InMemoryProductRepository $products;
    private RecordingEventBus $events;
    private UpdateProductHandler $handler;
    private string $sellerId;
    private Product $product;

    protected function setUp(): void
    {
        $this->products = new InMemoryProductRepository();
        $this->events = new RecordingEventBus();
        $this->handler = new UpdateProductHandler($this->products, $this->events);

        $this->sellerId = (string) Uuid::v7();
        $this->product = Product::create(ProductId::generate(), $this->sellerId, 'Mouse', 'Wired', Money::of(1000, 'USD'), 5);
        $this->product->pullDomainEvents(); // discard ProductCreated
        $this->products->save($this->product);
    }

    #[Test]
    public function the_owner_can_change_price_stock_and_visibility(): void
    {
        ($this->handler)(new UpdateProduct(
            productId: (string) $this->product->id(),
            sellerId: $this->sellerId,
            priceMinor: 1500,
            stock: 20,
            active: false,
        ));

        $updated = $this->products->get($this->product->id());
        self::assertSame(1500, $updated->price()->amountMinor);
        self::assertSame(20, $updated->stock());
        self::assertFalse($updated->isActive());
        self::assertCount(1, $this->events->ofType(ProductPriceChanged::class));
    }

    #[Test]
    public function a_non_owner_cannot_update_the_product(): void
    {
        $this->expectException(AccessDeniedException::class);

        ($this->handler)(new UpdateProduct(
            productId: (string) $this->product->id(),
            sellerId: (string) Uuid::v7(), // a different seller
            priceMinor: 9999,
        ));
    }

    #[Test]
    public function an_unchanged_price_records_no_event(): void
    {
        ($this->handler)(new UpdateProduct(
            productId: (string) $this->product->id(),
            sellerId: $this->sellerId,
            priceMinor: 1000, // same as current
        ));

        self::assertSame([], $this->events->ofType(ProductPriceChanged::class));
    }
}
