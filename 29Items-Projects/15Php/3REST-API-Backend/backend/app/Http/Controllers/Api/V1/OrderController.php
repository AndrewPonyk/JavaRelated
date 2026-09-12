<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Requests\V1\StoreOrderRequest;
use App\Http\Resources\V1\OrderResource;
use App\Models\Order;
use App\Services\OrderService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\AnonymousResourceCollection;
use OpenApi\Attributes as OA;

/**
 * Checkout & order history. Placing an order runs the transactional
 * OrderService and emits ProductPurchased (telemetry + notifications).
 */
class OrderController extends Controller
{
    public function __construct(private readonly OrderService $orders) {}

    #[OA\Get(
        path: '/orders',
        summary: "List the authenticated user's orders",
        security: [['sanctum' => []]],
        tags: ['Orders'],
        responses: [new OA\Response(response: 200, description: 'Paginated orders')],
    )]
    public function index(Request $request): AnonymousResourceCollection
    {
        $orders = $request->user()->orders()
            ->with('items.product')
            ->latest()
            ->paginate(15);

        return OrderResource::collection($orders);
    }

    #[OA\Post(
        path: '/orders',
        summary: 'Place an order (checkout)',
        security: [['sanctum' => []]],
        tags: ['Orders'],
        requestBody: new OA\RequestBody(
            required: true,
            content: new OA\JsonContent(
                required: ['items'],
                properties: [new OA\Property(
                    property: 'items',
                    type: 'array',
                    items: new OA\Items(properties: [
                        new OA\Property(property: 'product_id', type: 'integer', example: 1),
                        new OA\Property(property: 'quantity', type: 'integer', example: 2),
                    ]),
                )],
            ),
        ),
        responses: [
            new OA\Response(response: 201, description: 'Order placed'),
            new OA\Response(response: 409, description: 'Insufficient stock'),
            new OA\Response(response: 422, description: 'Validation failed'),
        ],
    )]
    public function store(StoreOrderRequest $request): JsonResponse
    {
        $order = $this->orders->place($request->user(), $request->validated()['items']);

        return (new OrderResource($order))->response()->setStatusCode(201);
    }

    #[OA\Get(
        path: '/orders/{id}',
        summary: 'Fetch a single order (owner/admin only)',
        security: [['sanctum' => []]],
        tags: ['Orders'],
        parameters: [new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer'))],
        responses: [
            new OA\Response(response: 200, description: 'OK'),
            new OA\Response(response: 403, description: 'Forbidden'),
            new OA\Response(response: 404, description: 'Not found'),
        ],
    )]
    public function show(Request $request, Order $order): OrderResource
    {
        $this->authorize('view', $order); // OrderPolicy

        return new OrderResource($order->load('items.product'));
    }
}
