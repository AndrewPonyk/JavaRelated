<?php

declare(strict_types=1);

namespace App\Ordering\UI\Http;

use Symfony\Component\Validator\Constraints as Assert;

final class PlaceOrderRequest
{
    #[Assert\NotBlank]
    #[Assert\Currency]
    public string $currency = 'USD';

    /**
     * @var list<array{productId: string, sellerId: string, quantity: int, unitPriceMinor: int}>
     */
    #[Assert\NotBlank]
    #[Assert\Count(min: 1)]
    #[Assert\All([
        new Assert\Collection(
            fields: [
                'productId' => [new Assert\NotBlank(), new Assert\Uuid()],
                'sellerId' => [new Assert\NotBlank(), new Assert\Uuid()],
                'quantity' => [new Assert\Positive()],
                'unitPriceMinor' => [new Assert\PositiveOrZero()],
            ],
            allowExtraFields: false,
        ),
    ])]
    public array $lines = [];
}
