<?php

declare(strict_types=1);

namespace App\Catalog\Domain\Repository;

use App\Catalog\Domain\Model\Product;
use App\Catalog\Domain\Model\ProductId;

/**
 * Port for Product persistence. Implemented by a Doctrine adapter in the
 * Infrastructure layer. The domain/application code depends only on this
 * interface (dependency inversion).
 */
interface ProductRepositoryInterface
{
    public function save(Product $product): void;

    public function get(ProductId $id): Product;

    public function find(ProductId $id): ?Product;

    /**
     * @return list<Product>
     */
    public function listBySeller(string $sellerId, int $page = 1, int $perPage = 20): array;

    /**
     * Active products for the public catalog (paginated).
     *
     * @return list<Product>
     */
    public function listActive(int $page = 1, int $perPage = 20): array;

    /**
     * Every product (source of truth) — used by the search reindex job.
     *
     * @return list<Product>
     */
    public function listAll(): array;
}
