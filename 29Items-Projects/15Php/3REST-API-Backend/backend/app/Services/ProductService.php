<?php

namespace App\Services;

use App\Models\Product;
use App\Models\User;
use App\Repositories\Contracts\ProductRepositoryInterface;
use Illuminate\Contracts\Pagination\LengthAwarePaginator;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;

/**
 * Application/business layer for products. Coordinates persistence (via the
 * repository), caching (Redis), and transactional integrity. Controllers
 * call into here; this class never touches HTTP.
 */
class ProductService
{
    private const LIST_CACHE_TTL = 300; // seconds

    public function __construct(private readonly ProductRepositoryInterface $repository) {}

    /**
     * @param  array<string, mixed>  $filters
     */
    public function paginate(array $filters, int $perPage): LengthAwarePaginator
    {
        // Cache hot list reads in Redis, tagged for surgical invalidation.
        $key = 'products:index:'.md5(json_encode($filters).":{$perPage}:".request('page', 1));

        return Cache::tags(['products'])->remember(
            $key,
            self::LIST_CACHE_TTL,
            fn () => $this->repository->paginate($filters, $perPage),
        );
    }

    /**
     * @param  array<string, mixed>  $attributes
     */
    public function create(array $attributes, User $owner): Product
    {
        $product = DB::transaction(function () use ($attributes, $owner) {
            $attributes['user_id'] = $owner->id;

            return $this->repository->create($attributes);
        });

        $this->forgetListCache();

        return $product;
    }

    /**
     * @param  array<string, mixed>  $attributes
     */
    public function update(Product $product, array $attributes): Product
    {
        $updated = $this->repository->update($product, $attributes);

        $this->forgetListCache();

        return $updated;
    }

    public function delete(Product $product): void
    {
        $this->repository->delete($product);

        $this->forgetListCache();
    }

    /**
     * Invalidate only the product list cache after a write — leaves unrelated
     * cache entries (rate limits, recommendations) untouched.
     */
    private function forgetListCache(): void
    {
        Cache::tags(['products'])->flush();
    }
}
