<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Requests\V1\StoreUserEventRequest;
use App\Models\UserEvent;
use Illuminate\Http\JsonResponse;
use OpenApi\Attributes as OA;

/**
 * Captures behavioral signals (views, wishlists, add-to-cart) that feed the
 * recommendation engine. The signal weight is server-assigned so clients
 * can't inflate their influence.
 */
class UserEventController extends Controller
{
    #[OA\Post(
        path: '/events',
        summary: 'Record a behavioral signal (view / add_to_cart / wishlist)',
        security: [['sanctum' => []]],
        tags: ['Events'],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                required: ['product_id', 'type'],
                properties: [
                    new OA\Property(property: 'product_id', type: 'integer', example: 1),
                    new OA\Property(property: 'type', type: 'string', enum: ['view', 'add_to_cart', 'wishlist'], example: 'view'),
                ],
            ),
        ),
        responses: [
            new OA\Response(response: 201, description: 'Signal recorded'),
            new OA\Response(response: 422, description: 'Validation failed'),
        ],
    )]
    public function store(StoreUserEventRequest $request): JsonResponse
    {
        $type = $request->validated('type');

        $event = UserEvent::create([
            'user_id' => $request->user()->id,
            'product_id' => $request->validated('product_id'),
            'type' => $type,
            'weight' => UserEvent::WEIGHTS[$type] ?? 1.0,
        ]);

        return response()->json([
            'id' => $event->id,
            'type' => $event->type,
            'product_id' => $event->product_id,
            'recorded' => true,
        ], 201);
    }
}
