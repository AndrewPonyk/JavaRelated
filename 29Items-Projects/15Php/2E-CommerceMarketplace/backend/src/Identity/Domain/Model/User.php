<?php

declare(strict_types=1);

namespace App\Identity\Domain\Model;

use App\Shared\Domain\Security\AuthenticatedActor;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;
use Symfony\Component\Security\Core\User\PasswordAuthenticatedUserInterface;
use Symfony\Component\Security\Core\User\UserInterface;

/**
 * Authenticated user. The user id (UUID string) is the JWT subject and is used
 * across contexts as customerId / sellerId. Roles drive the security hierarchy
 * (ROLE_CUSTOMER < ROLE_SELLER < ROLE_ADMIN).
 */
#[ORM\Entity]
#[ORM\Table(name: 'identity_users')]
#[ORM\UniqueConstraint(name: 'uniq_user_email', columns: ['email'])]
class User implements UserInterface, PasswordAuthenticatedUserInterface, AuthenticatedActor
{
    public const string ROLE_CUSTOMER = 'ROLE_CUSTOMER';
    public const string ROLE_SELLER = 'ROLE_SELLER';
    public const string ROLE_ADMIN = 'ROLE_ADMIN';

    /** Roles a user may self-assign at registration (ROLE_ADMIN is granted out-of-band). */
    private const array SELF_ASSIGNABLE_ROLES = [self::ROLE_CUSTOMER, self::ROLE_SELLER];

    #[ORM\Id]
    #[ORM\Column(type: Types::STRING, length: 36)]
    private string $id;

    #[ORM\Column(type: Types::STRING, length: 180)]
    private string $email;

    /** @var list<string> */
    #[ORM\Column(type: Types::JSON)]
    private array $roles;

    #[ORM\Column(type: Types::STRING)]
    private string $password = '';

    /**
     * @param list<string> $roles
     */
    public function __construct(string $id, string $email, array $roles = [self::ROLE_CUSTOMER])
    {
        $this->id = $id;
        $this->email = strtolower(trim($email));
        $this->roles = $roles ?: [self::ROLE_CUSTOMER];
    }

    /**
     * Factory used by registration. Filters roles down to the self-assignable set
     * so a client can never escalate to ROLE_ADMIN through the public API.
     *
     * @param list<string> $requestedRoles
     */
    public static function register(string $id, string $email, array $requestedRoles): self
    {
        $roles = array_values(array_intersect($requestedRoles, self::SELF_ASSIGNABLE_ROLES));

        return new self($id, $email, $roles ?: [self::ROLE_CUSTOMER]);
    }

    public function id(): string
    {
        return $this->id;
    }

    public function getUserIdentifier(): string
    {
        // Must match the security provider's lookup property (email). The JWT
        // subject is therefore the email; cross-context code uses id() instead.
        \assert('' !== $this->email); // a user is never persisted without an email

        return $this->email;
    }

    public function getEmail(): string
    {
        return $this->email;
    }

    /** @return list<string> */
    public function getRoles(): array
    {
        return array_values(array_unique([...$this->roles, self::ROLE_CUSTOMER]));
    }

    public function isSeller(): bool
    {
        return \in_array(self::ROLE_SELLER, $this->getRoles(), true);
    }

    public function getPassword(): string
    {
        return $this->password;
    }

    public function setHashedPassword(string $hashed): void
    {
        $this->password = $hashed;
    }

    public function eraseCredentials(): void
    {
        // No plaintext credentials retained.
    }
}
