<?php

declare(strict_types=1);

namespace App\Ordering\UI\Http;

use App\Ordering\Application\Command\PlaceOrder;
use App\Ordering\Application\Query\GetOrder;
use App\Ordering\Domain\Model\OrderId;
use App\Shared\Domain\Bus\Command\CommandBus;
use App\Shared\Domain\Bus\Query\QueryBus;
use App\Shared\Domain\Security\AccessDeniedException;
use App\Shared\Domain\Security\AuthenticatedActor;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Attribute\MapRequestPayload;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/orders', name: 'ordering_order_')]
final class OrderController extends AbstractController
{
    private const string UUID_REQUIREMENT = '[0-9a-fA-F-]{36}';

    public function __construct(
        private readonly CommandBus $commandBus,
        private readonly QueryBus $queryBus,
    ) {
    }

    /**
     * Place an order. Returns 202 Accepted: the order is recorded synchronously,
     * but fraud assessment and downstream effects run asynchronously, so the
     * order starts in PENDING and may transition after processing.
     */
    #[Route('', name: 'place', methods: ['POST'])]
    #[IsGranted('ROLE_CUSTOMER')]
    public function place(#[MapRequestPayload] PlaceOrderRequest $request): JsonResponse
    {
        $orderId = OrderId::generate();
        $customerId = $this->actor()->id();

        $this->commandBus->dispatch(new PlaceOrder(
            orderId: (string) $orderId,
            customerId: $customerId,
            currency: strtoupper($request->currency),
            lines: $request->lines,
        ));

        return $this->json(
            ['id' => (string) $orderId, 'status' => 'PENDING'],
            Response::HTTP_ACCEPTED,
            ['Location' => '/api/orders/'.$orderId],
        );
    }

    /**
     * Order detail, reconstructed by replaying its event stream. A customer may
     * only read their own orders; admins may read any.
     */
    #[Route('/{id}', name: 'get', methods: ['GET'], requirements: ['id' => self::UUID_REQUIREMENT])]
    #[IsGranted('ROLE_CUSTOMER')]
    public function get(string $id): JsonResponse
    {
        $view = $this->queryBus->ask(new GetOrder($id));

        if ($view->customerId !== $this->actor()->id() && !$this->isGranted('ROLE_ADMIN')) {
            throw AccessDeniedException::notOwner('order');
        }

        return $this->json($view->toArray());
    }

    private function actor(): AuthenticatedActor
    {
        $actor = $this->getUser();
        \assert($actor instanceof AuthenticatedActor); // guaranteed by #[IsGranted]

        return $actor;
    }
}
