<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Repository;

use RuntimeException;

/** Raised when an event stream was modified concurrently (optimistic lock fail). */
final class ConcurrencyException extends RuntimeException
{
    public static function streamChanged(string $streamId, int $expected, int $actual): self
    {
        return new self(\sprintf(
            'Concurrency conflict on stream "%s": expected version %d, found %d.',
            $streamId,
            $expected,
            $actual,
        ));
    }
}
