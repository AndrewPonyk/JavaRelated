<?php

declare(strict_types=1);

namespace App\FraudDetection\UI\Http;

use App\FraudDetection\Application\Command\ResolveFraudReview;
use App\FraudDetection\Application\Query\ListPendingFraudReviews;
use App\Shared\Domain\Bus\Command\CommandBus;
use App\Shared\Domain\Bus\Query\QueryBus;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpKernel\Attribute\MapRequestPayload;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/** Admin manual-review queue for flagged orders. */
#[Route('/admin/fraud', name: 'fraud_admin_')]
#[IsGranted('ROLE_ADMIN')]
final class FraudReviewController extends AbstractController
{
    private const string UUID_REQUIREMENT = '[0-9a-fA-F-]{36}';

    public function __construct(
        private readonly CommandBus $commandBus,
        private readonly QueryBus $queryBus,
    ) {
    }

    /** Pending reviews (most recent first). */
    #[Route('/reviews', name: 'list', methods: ['GET'])]
    public function list(): JsonResponse
    {
        $views = $this->queryBus->ask(new ListPendingFraudReviews());

        return $this->json(['items' => array_map(static fn ($v) => $v->toArray(), $views)]);
    }

    /** Resolve a flagged order (clear it or confirm fraud). */
    #[Route('/reviews/{orderId}/resolve', name: 'resolve', methods: ['POST'], requirements: ['orderId' => self::UUID_REQUIREMENT])]
    public function resolve(string $orderId, #[MapRequestPayload] ResolveReviewRequest $request): JsonResponse
    {
        $this->commandBus->dispatch(new ResolveFraudReview($orderId, $request->cleared));

        return $this->json(['orderId' => $orderId, 'resolved' => true, 'cleared' => $request->cleared]);
    }
}
