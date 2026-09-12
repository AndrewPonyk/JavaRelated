<?php

declare(strict_types=1);

namespace App\Tests\Support;

use App\Vendor\Domain\Model\Seller;
use App\Vendor\Domain\Repository\SellerRepository;

final class InMemorySellerRepository implements SellerRepository
{
    /** @var array<string, Seller> */
    private array $sellers = [];

    public function add(Seller $seller): void
    {
        $this->sellers[$seller->id()] = $seller;
    }

    public function find(string $id): ?Seller
    {
        return $this->sellers[$id] ?? null;
    }
}
