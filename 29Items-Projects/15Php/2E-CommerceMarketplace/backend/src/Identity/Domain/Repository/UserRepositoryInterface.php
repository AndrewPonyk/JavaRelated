<?php

declare(strict_types=1);

namespace App\Identity\Domain\Repository;

use App\Identity\Domain\Model\User;

/**
 * Port for User persistence. Implemented by a Doctrine adapter in Infrastructure.
 * The security layer uses its own entity provider for login; this port is for the
 * application's own reads/writes (registration, lookups).
 */
interface UserRepositoryInterface
{
    public function add(User $user): void;

    public function findByEmail(string $email): ?User;

    public function findById(string $id): ?User;
}
