<?php

declare(strict_types=1);

namespace App\Identity\Domain\Model;

use DomainException;

/** Raised when registering an email that already has an account. Mapped to 409. */
final class EmailAlreadyInUseException extends DomainException
{
    public static function withEmail(string $email): self
    {
        return new self(\sprintf('An account with email "%s" already exists.', $email));
    }
}
