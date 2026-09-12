<?php

declare(strict_types=1);

namespace App\Tests\Unit\Catalog;

use App\Catalog\Domain\Event\ProductCreated;
use App\Catalog\Domain\Model\Product;
use App\Catalog\Domain\Model\ProductId;
use App\Shared\Domain\ValueObject\Money;
use DomainException;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;

final class ProductTest extends TestCase
{
    #[Test]
    public function creating_a_product_records_a_product_created_event(): void
    {
        $product = Product::create(
            ProductId::generate(),
            sellerId: 'seller-1',
            name: 'Wireless Mouse',
            description: 'Ergonomic',
            price: Money::of(2599, 'USD'),
            stock: 10,
        );

        $events = $product->pullDomainEvents();

        self::assertCount(1, $events);
        self::assertInstanceOf(ProductCreated::class, $events[0]);
        self::assertSame('Wireless Mouse', $events[0]->name);
    }

    #[Test]
    public function it_rejects_an_empty_name(): void
    {
        $this->expectException(DomainException::class);

        Product::create(ProductId::generate(), 'seller-1', '   ', '', Money::zero('USD'), 1);
    }

    #[Test]
    public function it_forbids_negative_stock(): void
    {
        $product = Product::create(ProductId::generate(), 'seller-1', 'Item', '', Money::zero('USD'), 1);

        $this->expectException(DomainException::class);
        $product->adjustStock(-5);
    }
}
