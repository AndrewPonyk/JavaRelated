<?php

namespace App\Models;

use Database\Factories\UserEventFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Raw behavioral telemetry (view / add_to_cart / purchase ...).
 * These rows are aggregated by BehaviorClusteringService into feature
 * vectors used for personalization. Append-only; never updated.
 *
 * @property int $id
 * @property int $user_id
 * @property int $product_id
 * @property string $type one of: view | add_to_cart | purchase | wishlist
 * @property float $weight relative importance used when building feature vectors
 */
class UserEvent extends Model
{
    /** @use HasFactory<UserEventFactory> */
    use HasFactory;

    public const TYPE_VIEW = 'view';

    public const TYPE_ADD_TO_CART = 'add_to_cart';

    public const TYPE_PURCHASE = 'purchase';

    public const TYPE_WISHLIST = 'wishlist';

    /** Default signal weights — purchase counts far more than a view. */
    public const WEIGHTS = [
        self::TYPE_VIEW => 1.0,
        self::TYPE_WISHLIST => 2.0,
        self::TYPE_ADD_TO_CART => 3.0,
        self::TYPE_PURCHASE => 5.0,
    ];

    protected $fillable = [
        'user_id',
        'product_id',
        'type',
        'weight',
        'context',
    ];

    protected function casts(): array
    {
        return [
            'weight' => 'float',
            'context' => 'array',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function product(): BelongsTo
    {
        return $this->belongsTo(Product::class);
    }
}
