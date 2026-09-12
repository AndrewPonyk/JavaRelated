<?php

declare(strict_types=1);

namespace App\Catalog\UI\Http;

use Symfony\Component\Validator\Constraints as Assert;

/** Partial-update payload. All fields optional; only provided fields are applied. */
final class UpdateProductRequest
{
    #[Assert\Length(min: 1, max: 255)]
    public ?string $name = null;

    #[Assert\Length(max: 5000)]
    public ?string $description = null;

    #[Assert\PositiveOrZero]
    public ?int $priceMinor = null;

    #[Assert\Currency]
    public ?string $currency = null;

    #[Assert\PositiveOrZero]
    public ?int $stock = null;

    public ?bool $active = null;
}
