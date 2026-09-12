<?php

namespace App\Models;

use Database\Factories\OrderFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * A buyer's order — the aggregate root for a checkout. Created
 * transactionally by OrderService::place() (docs/ARCHITECTURE.md §2.3.2).
 *
 * @property int $id
 * @property int $user_id
 * @property int $total_cents
 * @property string $currency
 * @property string $status pending | paid | cancelled
 */
class Order extends Model
{
    /** @use HasFactory<OrderFactory> */
    use HasFactory;

    public const STATUS_PENDING = 'pending';

    public const STATUS_PAID = 'paid';

    public const STATUS_CANCELLED = 'cancelled';

    protected $fillable = [
        'user_id',
        'total_cents',
        'currency',
        'status',
    ];

    protected function casts(): array
    {
        return [
            'total_cents' => 'integer',
        ];
    }

    public function buyer(): BelongsTo
    {
        return $this->belongsTo(User::class, 'user_id');
    }

    public function items(): HasMany
    {
        return $this->hasMany(OrderItem::class);
    }

    public function total(): float
    {
        return $this->total_cents / 100;
    }
}
