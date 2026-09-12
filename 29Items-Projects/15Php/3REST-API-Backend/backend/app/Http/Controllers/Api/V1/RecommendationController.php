<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Resources\V1\RecommendationResource;
use App\Services\Recommendation\RecommendationService;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use OpenApi\Attributes as OA;

/**
 * Personalized product recommendations. Served from the precomputed
 * recommendations table via a cached read — clustering never runs here
 * (docs/ARCHITECTURE.md §2.3.3).
 */
class RecommendationController extends Controller
{
    public function __construct(private readonly RecommendationService $recommendations) {}

    #[OA\Get(
        path: '/recommendations',
        summary: 'Personalized recommendations for the authenticated user',
        security: [['sanctum' => []]],
        tags: ['Recommendations'],
        parameters: [
            new OA\Parameter(name: 'limit', in: 'query', required: false, schema: new OA\Schema(type: 'integer', default: 10)),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Recommended products (with cold-start fallback)'),
            new OA\Response(response: 401, description: 'Unauthenticated'),
        ],
    )]
    public function index(Request $request): AnonymousResourceCollection
    {
        $limit = $request->integer('limit') ?: null;

        $recommendations = $this->recommendations->forUser($request->user(), $limit);

        return RecommendationResource::collection($recommendations);
    }
}
