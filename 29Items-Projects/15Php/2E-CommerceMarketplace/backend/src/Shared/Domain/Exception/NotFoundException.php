<?php

declare(strict_types=1);

namespace App\Shared\Domain\Exception;

use RuntimeException;

/**
 * Base for "aggregate/resource not found" errors across contexts. The UI layer
 * maps any NotFoundException to HTTP 404 (see ApiExceptionListener), so contexts
 * never need to know about HTTP status codes.
 */
abstract class NotFoundException extends RuntimeException
{
}
