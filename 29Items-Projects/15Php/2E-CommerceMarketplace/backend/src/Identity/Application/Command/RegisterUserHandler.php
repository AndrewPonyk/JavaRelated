<?php

declare(strict_types=1);

namespace App\Identity\Application\Command;

use App\Identity\Domain\Event\UserRegistered;
use App\Identity\Domain\Model\EmailAlreadyInUseException;
use App\Identity\Domain\Model\User;
use App\Identity\Domain\Repository\UserRepositoryInterface;
use App\Shared\Domain\Bus\Event\EventBus;
use DateTimeImmutable;
use Symfony\Component\Messenger\Attribute\AsMessageHandler;
use Symfony\Component\PasswordHasher\Hasher\UserPasswordHasherInterface;

/**
 * Registers a new account: enforces email uniqueness, hashes the password with
 * argon2id (see security.yaml), persists, and publishes {@see UserRegistered} so
 * downstream contexts (e.g. Vendor seller provisioning) can react asynchronously.
 */
#[AsMessageHandler(bus: 'command.bus')]
final readonly class RegisterUserHandler
{
    public function __construct(
        private UserRepositoryInterface $users,
        private UserPasswordHasherInterface $passwordHasher,
        private EventBus $eventBus,
    ) {
    }

    public function __invoke(RegisterUser $command): void
    {
        $email = strtolower(trim($command->email));

        if (null !== $this->users->findByEmail($email)) {
            throw EmailAlreadyInUseException::withEmail($email);
        }

        $user = User::register($command->userId, $email, $command->roles);
        $user->setHashedPassword($this->passwordHasher->hashPassword($user, $command->plainPassword));

        $this->users->add($user);

        $this->eventBus->publish(new UserRegistered(
            userId: $user->id(),
            email: $user->getEmail(),
            roles: $user->getRoles(),
            occurredOn: new DateTimeImmutable(),
        ));
    }
}
