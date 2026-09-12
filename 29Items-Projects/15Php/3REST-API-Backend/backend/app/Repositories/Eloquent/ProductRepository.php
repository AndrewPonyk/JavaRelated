<?php

namespace App\Repositories\Eloquent;

use App\Models\Product;
use App\Repositories\Contracts\ProductRepositoryInterface;
use Illuminate\Contracts\Pagination\LengthAwarePaginator;

/**
 * The only place in the app that issues Eloquent queries for products.
 * Centralizing this keeps query concerns (eager loading, indexes, scopes)
 * out of controllers and services.
 */
class ProductRepository implements ProductRepositoryInterface
{
    public function paginate(array $filters, int $perPage): LengthAwarePaginator
    {
        return Product::query()
            ->active()
            ->when(
                $filters['category'] ?? null,
                fn ($q, $category) => $q->where('category', $category),
            )
            ->when(
                $filters['q'] ?? null,
                fn ($q, $term) => $q->where('name', 'like', "%{$term}%"),
            )
            ->with('owner') // eager load to avoid N+1 in the Resource
            ->latest()
            ->paginate(min($perPage, 100)); // hard cap protects the DB
    }

    public function find(int $id): ?Product
    {
        return Product::find($id);
    }

    public function create(array $attributes): Product
    {
        return Product::create($attributes);
    }

    public function update(Product $product, array $attributes): Product
    {
        $product->update($attributes);

        return $product->refresh();
    }

    public function delete(Product $product): void
    {
        $product->delete(); // soft delete (see Product::SoftDeletes)
    }
}
