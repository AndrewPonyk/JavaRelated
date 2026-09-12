<?php

declare(strict_types=1);

namespace App\Shared\Domain\Bus\Query;

/**
 * Marker for query messages. A query asks for data and never mutates state;
 * it is handled by exactly one handler and returns a read DTO.
 *
 * The result type is carried as a covariant generic so the {@see QueryBus} can
 * statically infer what each query returns at the call site (PHPStan L9).
 *
 * @template-covariant TResult
 */
interface Query
{
}
