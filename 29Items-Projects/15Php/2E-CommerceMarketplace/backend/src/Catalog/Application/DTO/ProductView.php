<?php

declare(strict_types=1);

namespace App\Catalog\Application\DTO;

use App\Catalog\Domain\Model\Product;

/** Read DTO returned to the UI layer. Decouples the API shape from the aggregate. */
final readonly class ProductView
{
    public function __construct(
        public string $id,
        public string $sellerId,
        public string $name,
        public string $description,
        public int $priceMinor,
        public string $currency,
        public int $stock,
        public bool $active,
    ) {
    }

    public static function fromAggregate(Product $product): self
    {
        return new self(
            (string) $product->id(),
            $product->sellerId(),
            $product->name(),
            $product->description(),
            $product->price()->amountMinor,
            $product->price()->currency,
            $product->stock(),
            $product->isActive(),
        );
    }

    /** @return array<string, mixed> */
    public function toArray(): array
    {
        return [
            'id' => $this->id,
            'sellerId' => $this->sellerId,
            'name' => $this->name,
            'description' => $this->description,
            'price' => ['amountMinor' => $this->priceMinor, 'currency' => $this->currency],
            'stock' => $this->stock,
            'active' => $this->active,
        ];
    }
}
