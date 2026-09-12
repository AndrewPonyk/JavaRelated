<?php

declare(strict_types=1);

namespace App\Catalog\Application\Query;

use App\Catalog\Application\DTO\ProductView;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'query.bus')]
final readonly class ListProductsBySellerHandler
{
    public function __construct(private ProductRepositoryInterface $products)
    {
    }

    /**
     * @return list<ProductView>
     */
    public function __invoke(ListProductsBySeller $query): array
    {
        return array_map(
            ProductView::fromAggregate(...),
            $this->products->listBySeller($query->sellerId, $query->page, $query->perPage),
        );
    }
}
