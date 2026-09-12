<?php

declare(strict_types=1);

namespace App\Catalog\Infrastructure\Persistence\Doctrine;

use App\Catalog\Domain\Model\Product;
use App\Catalog\Domain\Model\ProductId;
use App\Catalog\Domain\Model\ProductNotFoundException;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;
use Doctrine\ORM\EntityManagerInterface;

/**
 * Doctrine adapter for {@see ProductRepositoryInterface}.
 *
 * The mapping lives in mapping/Product.orm.xml so the Product aggregate itself
 * carries no ORM metadata. Note: we do not flush here — the command bus'
 * doctrine_transaction middleware commits the unit of work.
 */
final class DoctrineProductRepository implements ProductRepositoryInterface
{
    public function __construct(private readonly EntityManagerInterface $em)
    {
    }

    public function save(Product $product): void
    {
        $this->em->persist($product);
    }

    public function get(ProductId $id): Product
    {
        return $this->find($id) ?? throw ProductNotFoundException::withId($id);
    }

    public function find(ProductId $id): ?Product
    {
        // The "product_id" custom type converts the ProductId for the lookup.
        return $this->em->find(Product::class, $id);
    }

    public function listBySeller(string $sellerId, int $page = 1, int $perPage = 20): array
    {
        /** @var list<Product> $result */
        $result = $this->em->createQuery(
            'SELECT p FROM '.Product::class.' p WHERE p.sellerId = :sid ORDER BY p.name ASC',
        )
            ->setParameter('sid', $sellerId)
            ->setFirstResult(max(0, ($page - 1) * $perPage))
            ->setMaxResults($perPage)
            ->getResult();

        return $result;
    }

    public function listActive(int $page = 1, int $perPage = 20): array
    {
        /** @var list<Product> $result */
        $result = $this->em->createQuery(
            'SELECT p FROM '.Product::class.' p WHERE p.active = true ORDER BY p.name ASC',
        )
            ->setFirstResult(max(0, ($page - 1) * $perPage))
            ->setMaxResults($perPage)
            ->getResult();

        return $result;
    }

    public function listAll(): array
    {
        /** @var list<Product> $result */
        $result = $this->em->createQuery(
            'SELECT p FROM '.Product::class.' p ORDER BY p.id ASC',
        )->getResult();

        return $result;
    }
}
