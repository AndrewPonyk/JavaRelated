<?php

declare(strict_types=1);

namespace App\Tests\Integration\Catalog;

use App\Catalog\Domain\Model\Product;
use App\Catalog\Domain\Model\ProductId;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;
use App\Shared\Domain\ValueObject\Money;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Test\KernelTestCase;
use Symfony\Component\Uid\Uuid;

/**
 * Integration test for the Doctrine product repository against a real PostgreSQL
 * (CI service container; wrapped in a rolled-back transaction by
 * dama/doctrine-test-bundle). Validates the XML mapping + embedded Money + the
 * custom ProductId DBAL type round-trip.
 */
final class ProductPersistenceTest extends KernelTestCase
{
    public function test_it_saves_and_retrieves_a_product(): void
    {
        self::bootKernel();
        $em = self::getContainer()->get(EntityManagerInterface::class);
        $repo = self::getContainer()->get(ProductRepositoryInterface::class);

        $id = ProductId::generate();
        $repo->save(Product::create($id, (string) Uuid::v7(), 'Widget', 'A widget', Money::of(1999, 'USD'), 7));
        $em->flush();
        $em->clear();

        $found = $repo->get($id);
        self::assertSame('Widget', $found->name());
        self::assertSame(1999, $found->price()->amountMinor);
        self::assertSame('USD', $found->price()->currency);
        self::assertTrue($found->isActive());
    }

    public function test_list_active_excludes_deactivated_products(): void
    {
        self::bootKernel();
        $em = self::getContainer()->get(EntityManagerInterface::class);
        $repo = self::getContainer()->get(ProductRepositoryInterface::class);
        $sellerId = (string) Uuid::v7();

        $active = Product::create(ProductId::generate(), $sellerId, 'Active', '', Money::of(100, 'USD'), 1);
        $hidden = Product::create(ProductId::generate(), $sellerId, 'Hidden', '', Money::of(100, 'USD'), 1);
        $hidden->deactivate();
        $repo->save($active);
        $repo->save($hidden);
        $em->flush();
        $em->clear();

        $names = array_map(static fn (Product $p): string => $p->name(), $repo->listActive());
        self::assertContains('Active', $names);
        self::assertNotContains('Hidden', $names);
    }
}
