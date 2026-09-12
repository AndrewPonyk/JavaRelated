<?php

declare(strict_types=1);

namespace App\Shared\Domain\Security;

use RuntimeException;

/**
 * Raised by the application layer when an authenticated actor tries to act on a
 * resource they do not own (e.g. a seller editing another seller's product, or a
 * customer reading another customer's order). Mapped to HTTP 403.
 *
 * Kept framework-free so the domain/application layers do not depend on Symfony
 * Security; the UI layer translates it (see ApiExceptionListener).
 */
final class AccessDeniedException extends RuntimeException
{
    public static function notOwner(string $what): self
    {
        return new self(\sprintf('You are not allowed to access this %s.', $what));
    }
}
