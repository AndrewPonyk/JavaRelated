<?php

declare(strict_types=1);

namespace App\Identity\UI\Http;

use App\Identity\Application\Command\RegisterUser;
use App\Identity\Domain\Model\User;
use App\Shared\Domain\Bus\Command\CommandBus;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Attribute\MapRequestPayload;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;
use Symfony\Component\Uid\Uuid;

/**
 * Authentication endpoints. Login itself is handled by the `login` firewall
 * (json_login → JWT, see security.yaml); this controller adds registration and
 * the authenticated-identity ("me") endpoint.
 */
#[Route('/auth', name: 'identity_auth_')]
final class AuthController extends AbstractController
{
    public function __construct(private readonly CommandBus $commandBus)
    {
    }

    /** Public self-service registration. Returns the new account (no token — client then logs in). */
    #[Route('/register', name: 'register', methods: ['POST'])]
    public function register(#[MapRequestPayload] RegisterRequest $request): JsonResponse
    {
        $userId = Uuid::v7()->toRfc4122();
        $roles = 'seller' === $request->accountType ? [User::ROLE_SELLER] : [User::ROLE_CUSTOMER];

        $this->commandBus->dispatch(new RegisterUser(
            userId: $userId,
            email: $request->email,
            plainPassword: $request->password,
            roles: $roles,
        ));

        return $this->json(
            ['id' => $userId, 'email' => strtolower(trim($request->email)), 'roles' => $roles],
            Response::HTTP_CREATED,
        );
    }

    /** The authenticated user's identity, decoded from their JWT. */
    #[Route('/me', name: 'me', methods: ['GET'])]
    #[IsGranted('ROLE_CUSTOMER')]
    public function me(): JsonResponse
    {
        $user = $this->getUser();
        \assert($user instanceof User);

        return $this->json([
            'id' => $user->id(),
            'email' => $user->getEmail(),
            'roles' => $user->getRoles(),
        ]);
    }
}
