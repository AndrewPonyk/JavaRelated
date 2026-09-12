<?php

namespace App\Models;

use Database\Factories\ProductFactory;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\SoftDeletes;

/**
 * @property int $id
 * @property int $user_id
 * @property string $name
 * @property string|null $description
 * @property int $price_cents Money is stored as integer minor units (see TECH-NOTES pitfall #12).
 * @property string $currency
 * @property string $category
 * @property int $stock
 * @property bool $is_active
 */
class Product extends Model
{
    /** @use HasFactory<ProductFactory> */
    use HasFactory, SoftDeletes;

    protected $fillable = [
        // user_id is set server-side from the authenticated owner; it is never
        // part of a validated request payload, so this can't be hijacked.
        'user_id',
        'name',
        'description',
        'price_cents',
        'currency',
        'category',
        'stock',
        'is_active',
    ];

    protected function casts(): array
    {
        return [
            'price_cents' => 'integer',
            'stock' => 'integer',
            'is_active' => 'boolean',
        ];
    }

    /** The seller who owns this listing. */
    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    /** Convenience accessor: decimal price derived from minor units. */
    public function price(): float
    {
        return $this->price_cents / 100;
    }

    /**
     * Local scope for the common "active only" filter.
     *
     * @param  Builder<Product>  $query
     * @return Builder<Product>
     */
    public function scopeActive($query)
    {
        return $query->where('is_active', true);
    }
}
