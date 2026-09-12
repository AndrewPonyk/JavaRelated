<?php

declare(strict_types=1);

namespace App\Catalog\Domain\Model;

use App\Catalog\Domain\Event\ProductCreated;
use App\Catalog\Domain\Event\ProductPriceChanged;
use App\Shared\Domain\Aggregate\AggregateRoot;
use App\Shared\Domain\ValueObject\Money;
use DateTimeImmutable;
use DomainException;

/**
 * Product aggregate root (Catalog context).
 *
 * Pure domain object: no Doctrine/Symfony imports. Persistence is mapped via
 * XML in Infrastructure/Persistence/Doctrine/mapping/Product.orm.xml, so this
 * model stays free of framework concerns. Invariants are enforced here, not in
 * the controller.
 */
class Product extends AggregateRoot
{
    private Money $price;
    private int $stock;
    private bool $active;

    private function __construct(
        private readonly ProductId $id,
        private readonly string $sellerId,
        private string $name,
        private string $description,
        Money $price,
        int $stock,
    ) {
        $this->guardName($name);
        $this->guardStock($stock);
        $this->price = $price;
        $this->stock = $stock;
        $this->active = true;
    }

    public static function create(
        ProductId $id,
        string $sellerId,
        string $name,
        string $description,
        Money $price,
        int $stock,
    ): self {
        $product = new self($id, $sellerId, $name, $description, $price, $stock);
        $product->recordThat(new ProductCreated(
            (string) $id,
            $sellerId,
            $name,
            $price->amountMinor,
            $price->currency,
            new DateTimeImmutable(),
        ));

        return $product;
    }

    public function changePrice(Money $newPrice): void
    {
        if ($this->price->equals($newPrice)) {
            return;
        }
        $old = $this->price;
        $this->price = $newPrice;
        $this->recordThat(new ProductPriceChanged(
            (string) $this->id,
            $old->amountMinor,
            $newPrice->amountMinor,
            $newPrice->currency,
            new DateTimeImmutable(),
        ));
    }

    public function adjustStock(int $delta): void
    {
        $next = $this->stock + $delta;
        $this->guardStock($next);
        $this->stock = $next;
    }

    public function changeStock(int $newStock): void
    {
        $this->guardStock($newStock);
        $this->stock = $newStock;
    }

    public function rename(string $name): void
    {
        $this->guardName($name);
        $this->name = $name;
    }

    public function describe(string $description): void
    {
        $this->description = $description;
    }

    public function activate(): void
    {
        $this->active = true;
    }

    public function deactivate(): void
    {
        $this->active = false;
    }

    public function id(): ProductId
    {
        return $this->id;
    }

    public function sellerId(): string
    {
        return $this->sellerId;
    }

    public function name(): string
    {
        return $this->name;
    }

    public function description(): string
    {
        return $this->description;
    }

    public function price(): Money
    {
        return $this->price;
    }

    public function stock(): int
    {
        return $this->stock;
    }

    public function isActive(): bool
    {
        return $this->active;
    }

    private function guardName(string $name): void
    {
        if ('' === trim($name)) {
            throw new DomainException('Product name cannot be empty.');
        }
    }

    private function guardStock(int $stock): void
    {
        if ($stock < 0) {
            throw new DomainException('Stock cannot be negative.');
        }
    }
}
