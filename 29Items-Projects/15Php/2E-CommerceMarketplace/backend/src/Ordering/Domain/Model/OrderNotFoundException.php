<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Model;

use App\Shared\Domain\Exception\NotFoundException;

/** Raised when an order stream does not exist in the event store. Mapped to 404. */
final class OrderNotFoundException extends NotFoundException
{
    public static function withId(OrderId $id): self
    {
        return new self(\sprintf('Order "%s" was not found.', $id));
    }
}
