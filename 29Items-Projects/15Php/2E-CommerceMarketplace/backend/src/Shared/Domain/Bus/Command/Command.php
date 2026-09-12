<?php

declare(strict_types=1);

namespace App\Shared\Domain\Bus\Command;

/**
 * Marker for command messages. A command expresses an intent to change state;
 * it is handled by exactly one handler, synchronously, inside a DB transaction.
 */
interface Command
{
}
