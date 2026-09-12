<?php

declare(strict_types=1);

namespace App\Tests\Support;

use App\Catalog\Domain\Model\Product;
use App\Catalog\Domain\Model\ProductId;
use App\Catalog\Domain\Model\ProductNotFoundException;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;

final class InMemoryProductRepository implements ProductRepositoryInterface
{
    /** @var array<string, Product> */
    private array $products = [];

    public function save(Product $product): void
    {
        $this->products[(string) $product->id()] = $product;
    }

    public function get(ProductId $id): Product
    {
        return $this->find($id) ?? throw ProductNotFoundException::withId($id);
    }

    public function find(ProductId $id): ?Product
    {
        return $this->products[(string) $id] ?? null;
    }

    public function listBySeller(string $sellerId, int $page = 1, int $perPage = 20): array
    {
        return array_values(array_filter(
            $this->products,
            static fn (Product $p): bool => $p->sellerId() === $sellerId,
        ));
    }

    public function listActive(int $page = 1, int $perPage = 20): array
    {
        return array_values(array_filter($this->products, static fn (Product $p): bool => $p->isActive()));
    }

    public function listAll(): array
    {
        return array_values($this->products);
    }
}
