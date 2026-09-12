<?php

declare(strict_types=1);

namespace App\Identity\Application\Command;

use App\Shared\Domain\Bus\Command\Command;

/** Intent to register a new account. The plaintext password is hashed by the handler. */
final readonly class RegisterUser implements Command
{
    /**
     * @param list<string> $roles
     */
    public function __construct(
        public string $userId,
        public string $email,
        public string $plainPassword,
        public array $roles,
    ) {
    }
}
