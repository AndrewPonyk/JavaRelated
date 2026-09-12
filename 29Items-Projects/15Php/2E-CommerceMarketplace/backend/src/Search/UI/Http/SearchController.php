<?php

declare(strict_types=1);

namespace App\Search\UI\Http;

use App\Search\Application\Query\SearchProducts;
use App\Shared\Domain\Bus\Query\QueryBus;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpKernel\Exception\TooManyRequestsHttpException;
use Symfony\Component\RateLimiter\RateLimiterFactory;
use Symfony\Component\Routing\Attribute\Route;

/** Public product search (read side). Served entirely from the ES read model. */
#[Route('/search', name: 'search_')]
final class SearchController extends AbstractController
{
    public function __construct(
        private readonly QueryBus $queryBus,
        private readonly RateLimiterFactory $searchLimiter,
    ) {
    }

    #[Route('', name: 'products', methods: ['GET'])]
    public function search(Request $request): JsonResponse
    {
        // Throttle search per client IP (see TECH-NOTES §3.6). 429 on exhaustion.
        $limiter = $this->searchLimiter->create($request->getClientIp() ?? 'anonymous');
        if (!$limiter->consume(1)->isAccepted()) {
            throw new TooManyRequestsHttpException();
        }

        $result = $this->queryBus->ask(new SearchProducts(
            term: (string) $request->query->get('q', ''),
            page: $request->query->getInt('page', 1),
            perPage: $request->query->getInt('perPage', 20),
        ));

        return $this->json($result);
    }
}
