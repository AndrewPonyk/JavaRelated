<?php

declare(strict_types=1);

namespace App\Shared\Domain\Security;

/**
 * Implemented by the security user (Identity\User). Lets UI controllers obtain
 * the stable actor id (used cross-context as customerId / sellerId) without
 * depending on the Identity context's concrete model — the actor's UUID, not
 * the login email, is the cross-context identifier.
 */
interface AuthenticatedActor
{
    public function id(): string;
}
