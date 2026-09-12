<?php

declare(strict_types=1);

namespace App\Tests\Unit\Identity;

use App\Identity\Domain\Model\User;
use PHPUnit\Framework\Attributes\Test;
use PHPUnit\Framework\TestCase;
use Symfony\Component\Uid\Uuid;

final class UserTest extends TestCase
{
    #[Test]
    public function registration_cannot_self_assign_the_admin_role(): void
    {
        $user = User::register(Uuid::v7()->toRfc4122(), 'eve@example.com', ['ROLE_ADMIN', 'ROLE_SELLER']);

        self::assertContains('ROLE_SELLER', $user->getRoles());
        self::assertContains('ROLE_CUSTOMER', $user->getRoles());
        self::assertNotContains('ROLE_ADMIN', $user->getRoles());
    }

    #[Test]
    public function registration_defaults_to_a_customer(): void
    {
        $user = User::register(Uuid::v7()->toRfc4122(), 'bob@example.com', []);

        self::assertSame(['ROLE_CUSTOMER'], $user->getRoles());
        self::assertFalse($user->isSeller());
    }

    #[Test]
    public function it_normalises_the_email(): void
    {
        $user = User::register(Uuid::v7()->toRfc4122(), '  Alice@Example.COM ', ['ROLE_SELLER']);

        self::assertSame('alice@example.com', $user->getEmail());
        self::assertTrue($user->isSeller());
    }
}
