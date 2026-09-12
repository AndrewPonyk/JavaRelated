<?php

declare(strict_types=1);

namespace App\Identity\Domain\Event;

use App\Shared\Domain\Event\DomainEvent;
use DateTimeImmutable;

/**
 * Published after a user successfully registers. Other contexts react to it —
 * e.g. the Vendor context provisions a Seller profile when a SELLER registers
 * (event-driven onboarding; no synchronous cross-context call).
 *
 * @phpstan-type Roles list<string>
 */
final readonly class UserRegistered implements DomainEvent
{
    /**
     * @param list<string> $roles
     */
    public function __construct(
        public string $userId,
        public string $email,
        public array $roles,
        public DateTimeImmutable $occurredOn,
    ) {
    }

    public function aggregateId(): string
    {
        return $this->userId;
    }

    public function occurredOn(): DateTimeImmutable
    {
        return $this->occurredOn;
    }

    public static function eventName(): string
    {
        return 'identity.user_registered';
    }
}
