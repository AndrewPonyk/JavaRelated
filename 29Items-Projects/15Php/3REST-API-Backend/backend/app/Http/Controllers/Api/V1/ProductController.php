<?php

namespace App\Http\Controllers\Api\V1;

use App\Http\Requests\V1\StoreProductRequest;
use App\Http\Requests\V1\UpdateProductRequest;
use App\Http\Resources\V1\ProductCollection;
use App\Http\Resources\V1\ProductResource;
use App\Models\Product;
use App\Services\ProductService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use OpenApi\Attributes as OA;

/**
 * Thin HTTP layer: validate (Form Request) → delegate (Service) → present (Resource).
 * No business logic or raw queries live here — see docs/ARCHITECTURE.md §2.2.
 */
class ProductController extends Controller
{
    public function __construct(private readonly ProductService $products) {}

    #[OA\Get(
        path: '/products',
        summary: 'List products (paginated, filterable)',
        security: [['sanctum' => []]],
        tags: ['Products'],
        parameters: [
            new OA\Parameter(name: 'category', in: 'query', required: false, schema: new OA\Schema(type: 'string')),
            new OA\Parameter(name: 'q', in: 'query', required: false, schema: new OA\Schema(type: 'string')),
            new OA\Parameter(name: 'per_page', in: 'query', required: false, schema: new OA\Schema(type: 'integer', default: 15)),
        ],
        responses: [
            new OA\Response(response: 200, description: 'Paginated product collection'),
            new OA\Response(response: 401, description: 'Unauthenticated'),
        ],
    )]
    public function index(Request $request): ProductCollection
    {
        $filters = $request->only(['category', 'q']);
        $perPage = (int) $request->integer('per_page', 15);

        return new ProductCollection(
            $this->products->paginate($filters, $perPage),
        );
    }

    #[OA\Post(
        path: '/products',
        summary: 'Create a product',
        security: [['sanctum' => []]],
        tags: ['Products'],
        responses: [
            new OA\Response(response: 201, description: 'Created'),
            new OA\Response(response: 422, description: 'Validation failed'),
        ],
    )]
    public function store(StoreProductRequest $request): JsonResponse
    {
        $product = $this->products->create(
            $request->validated(),
            $request->user(),
        );

        return (new ProductResource($product))
            ->response()
            ->setStatusCode(201);
    }

    #[OA\Get(
        path: '/products/{id}',
        summary: 'Fetch a single product',
        security: [['sanctum' => []]],
        tags: ['Products'],
        parameters: [new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer'))],
        responses: [
            new OA\Response(response: 200, description: 'OK'),
            new OA\Response(response: 404, description: 'Not found'),
        ],
    )]
    public function show(Product $product): ProductResource
    {
        // Route-model binding already 404s on a missing id.
        return new ProductResource($product->load('owner'));
    }

    #[OA\Put(
        path: '/products/{id}',
        summary: 'Update a product (owner/admin only)',
        security: [['sanctum' => []]],
        tags: ['Products'],
        parameters: [new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer'))],
        responses: [
            new OA\Response(response: 200, description: 'Updated'),
            new OA\Response(response: 403, description: 'Forbidden'),
            new OA\Response(response: 422, description: 'Validation failed'),
        ],
    )]
    public function update(UpdateProductRequest $request, Product $product): ProductResource
    {
        // Authorization is enforced inside UpdateProductRequest::authorize().
        $updated = $this->products->update($product, $request->validated());

        return new ProductResource($updated);
    }

    #[OA\Delete(
        path: '/products/{id}',
        summary: 'Delete a product (owner/admin only)',
        security: [['sanctum' => []]],
        tags: ['Products'],
        parameters: [new OA\Parameter(name: 'id', in: 'path', required: true, schema: new OA\Schema(type: 'integer'))],
        responses: [
            new OA\Response(response: 204, description: 'Deleted'),
            new OA\Response(response: 403, description: 'Forbidden'),
        ],
    )]
    public function destroy(Request $request, Product $product): JsonResponse
    {
        $this->authorize('delete', $product); // ProductPolicy

        $this->products->delete($product);

        return response()->json(null, 204);
    }
}
