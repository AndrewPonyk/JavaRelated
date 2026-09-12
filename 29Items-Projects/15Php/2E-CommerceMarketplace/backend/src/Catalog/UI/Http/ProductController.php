<?php

declare(strict_types=1);

namespace App\Catalog\UI\Http;

use App\Catalog\Application\Command\CreateProduct;
use App\Catalog\Application\Command\UpdateProduct;
use App\Catalog\Application\Query\GetProduct;
use App\Catalog\Application\Query\ListProducts;
use App\Catalog\Application\Query\ListProductsBySeller;
use App\Catalog\Domain\Model\ProductId;
use App\Shared\Domain\Bus\Command\CommandBus;
use App\Shared\Domain\Bus\Query\QueryBus;
use App\Shared\Domain\Security\AuthenticatedActor;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\HttpKernel\Attribute\MapRequestPayload;
use Symfony\Component\Routing\Attribute\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

/**
 * REST API for products (Catalog context).
 *
 * Thin controller: it validates input (via the typed request payload + the
 * Security layer), translates HTTP ⇄ application messages through the command/
 * query buses, and never touches the domain or persistence directly.
 */
#[Route('/products', name: 'catalog_product_')]
final class ProductController extends AbstractController
{
    private const string UUID_REQUIREMENT = '[0-9a-fA-F-]{36}';

    public function __construct(
        private readonly CommandBus $commandBus,
        private readonly QueryBus $queryBus,
    ) {
    }

    /** Public catalog listing of active products (paginated). */
    #[Route('', name: 'list', methods: ['GET'])]
    public function list(Request $request): JsonResponse
    {
        $views = $this->queryBus->ask(new ListProducts(
            page: $request->query->getInt('page', 1),
            perPage: $request->query->getInt('perPage', 20),
        ));

        return $this->json(['items' => array_map(static fn ($v) => $v->toArray(), $views)]);
    }

    /** The authenticated seller's own products (paginated). Declared before {id}. */
    #[Route('/mine', name: 'list_mine', methods: ['GET'])]
    #[IsGranted('ROLE_SELLER')]
    public function listMine(Request $request): JsonResponse
    {
        $actor = $this->actor();

        $views = $this->queryBus->ask(new ListProductsBySeller(
            sellerId: $actor->id(),
            page: $request->query->getInt('page', 1),
            perPage: $request->query->getInt('perPage', 20),
        ));

        return $this->json(['items' => array_map(static fn ($v) => $v->toArray(), $views)]);
    }

    /** Public single-product read. */
    #[Route('/{id}', name: 'get', methods: ['GET'], requirements: ['id' => self::UUID_REQUIREMENT])]
    public function get(string $id): JsonResponse
    {
        return $this->json($this->queryBus->ask(new GetProduct($id))->toArray());
    }

    /** Create a product. Only sellers may create; the product is owned by them. */
    #[Route('', name: 'create', methods: ['POST'])]
    #[IsGranted('ROLE_SELLER')]
    public function create(#[MapRequestPayload] CreateProductRequest $request): JsonResponse
    {
        $productId = ProductId::generate();
        $sellerId = $this->actor()->id();

        $this->commandBus->dispatch(new CreateProduct(
            productId: (string) $productId,
            sellerId: $sellerId,
            name: $request->name,
            description: $request->description,
            priceMinor: $request->priceMinor,
            currency: strtoupper($request->currency),
            stock: $request->stock,
        ));

        // 201 + Location; the read model (search) becomes consistent asynchronously.
        return $this->json(
            ['id' => (string) $productId],
            Response::HTTP_CREATED,
            ['Location' => '/api/products/'.$productId],
        );
    }

    /** Partially update a product. Seller-only; ownership enforced in the handler. */
    #[Route('/{id}', name: 'update', methods: ['PATCH'], requirements: ['id' => self::UUID_REQUIREMENT])]
    #[IsGranted('ROLE_SELLER')]
    public function update(string $id, #[MapRequestPayload] UpdateProductRequest $request): JsonResponse
    {
        $this->commandBus->dispatch(new UpdateProduct(
            productId: $id,
            sellerId: $this->actor()->id(),
            name: $request->name,
            description: $request->description,
            priceMinor: $request->priceMinor,
            currency: $request->currency,
            stock: $request->stock,
            active: $request->active,
        ));

        return $this->json($this->queryBus->ask(new GetProduct($id))->toArray());
    }

    private function actor(): AuthenticatedActor
    {
        $actor = $this->getUser();
        \assert($actor instanceof AuthenticatedActor); // guaranteed by #[IsGranted]

        return $actor;
    }
}
