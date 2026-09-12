<?php

declare(strict_types=1);

namespace App\Catalog\Application\Query;

use App\Catalog\Application\DTO\ProductView;
use App\Catalog\Domain\Model\ProductId;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'query.bus')]
final readonly class GetProductHandler
{
    public function __construct(private ProductRepositoryInterface $products)
    {
    }

    public function __invoke(GetProduct $query): ProductView
    {
        // get() throws ProductNotFoundException (→ HTTP 404) when absent.
        return ProductView::fromAggregate($this->products->get(ProductId::fromString($query->productId)));
    }
}
