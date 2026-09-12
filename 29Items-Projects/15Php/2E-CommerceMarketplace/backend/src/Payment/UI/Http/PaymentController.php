<?php

declare(strict_types=1);

namespace App\Payment\UI\Http;

use App\Payment\Application\Command\CapturePayment;
use App\Payment\Application\Query\GetPaymentByIdempotencyKey;
use App\Shared\Domain\Bus\Command\CommandBus;
use App\Shared\Domain\Bus\Query\QueryBus;
use App\Shared\Domain\Security\AuthenticatedActor;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Attribute\MapRequestPayload;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;
use Symfony\Component\Uid\Uuid;

/** Payment capture endpoint. A fraud BLOCK hold yields 409; a PSP decline yields 402. */
#[Route('/payments', name: 'payment_')]
final class PaymentController extends AbstractController
{
    public function __construct(
        private readonly CommandBus $commandBus,
        private readonly QueryBus $queryBus,
    ) {
    }

    #[Route('/capture', name: 'capture', methods: ['POST'])]
    #[IsGranted('ROLE_CUSTOMER')]
    public function capture(#[MapRequestPayload] CapturePaymentRequest $request): JsonResponse
    {
        $actor = $this->getUser();
        \assert($actor instanceof AuthenticatedActor);

        $idempotencyKey = $request->idempotencyKey ?? Uuid::v7()->toRfc4122();

        // A fraud hold throws PaymentBlockedException → 409 (mapped centrally).
        $this->commandBus->dispatch(new CapturePayment(
            orderId: $request->orderId,
            customerId: $actor->id(),
            amountMinor: $request->amountMinor,
            currency: strtoupper($request->currency),
            paymentMethodToken: $request->paymentMethodToken,
            idempotencyKey: $idempotencyKey,
        ));

        $view = $this->queryBus->ask(new GetPaymentByIdempotencyKey($idempotencyKey));
        \assert(null !== $view); // the handler always records a transaction when not blocked

        $status = $view->isCaptured() ? Response::HTTP_OK : Response::HTTP_PAYMENT_REQUIRED;

        return $this->json($view->toArray() + ['idempotencyKey' => $idempotencyKey], $status);
    }
}
