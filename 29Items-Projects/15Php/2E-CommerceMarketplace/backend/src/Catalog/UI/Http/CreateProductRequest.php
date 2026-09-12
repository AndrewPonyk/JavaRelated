<?php

declare(strict_types=1);

namespace App\Catalog\UI\Http;

use Symfony\Component\Validator\Constraints as Assert;

/**
 * Inbound request payload for creating a product. Validated at the HTTP boundary
 * by Symfony's Validator (mapped from JSON via #[MapRequestPayload]). Domain
 * invariants are *additionally* enforced inside the Product aggregate.
 */
final class CreateProductRequest
{
    #[Assert\NotBlank]
    #[Assert\Length(max: 255)]
    public string $name = '';

    #[Assert\Length(max: 5000)]
    public string $description = '';

    #[Assert\NotNull]
    #[Assert\PositiveOrZero]
    public int $priceMinor = 0;

    #[Assert\NotBlank]
    #[Assert\Currency]
    public string $currency = 'USD';

    #[Assert\PositiveOrZero]
    public int $stock = 0;
}
