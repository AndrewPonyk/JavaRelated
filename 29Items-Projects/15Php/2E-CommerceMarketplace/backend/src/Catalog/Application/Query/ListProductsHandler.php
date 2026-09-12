<?php

declare(strict_types=1);

namespace App\Catalog\Application\Query;

use App\Catalog\Application\DTO\ProductView;
use App\Catalog\Domain\Repository\ProductRepositoryInterface;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;

#[AsMessageHandler(bus: 'query.bus')]
final readonly class ListProductsHandler
{
    public function __construct(private ProductRepositoryInterface $products)
    {
    }

    /**
     * @return list<ProductView>
     */
    public function __invoke(ListProducts $query): array
    {
        return array_map(
            ProductView::fromAggregate(...),
            $this->products->listActive(max(1, $query->page), min(100, max(1, $query->perPage))),
        );
    }
}
