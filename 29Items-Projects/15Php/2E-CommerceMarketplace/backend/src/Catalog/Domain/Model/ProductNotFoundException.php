<?php

declare(strict_types=1);

namespace App\Catalog\Domain\Model;

use App\Shared\Domain\Exception\NotFoundException;

/** Thrown when a product aggregate cannot be found. Mapped to HTTP 404 in UI. */
final class ProductNotFoundException extends NotFoundException
{
    public static function withId(ProductId $id): self
    {
        return new self(\sprintf('Product "%s" was not found.', $id));
    }
}
