<?php

namespace App\Models;

use Database\Factories\RecommendationFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A precomputed recommendation row (user → product) produced by the
 * clustering pipeline. Read directly by the API; never computed inline
 * (docs/TECH-NOTES.md pitfall #10).
 *
 * @property int $id
 * @property int $user_id
 * @property int $product_id
 * @property int $cluster
 * @property float $score
 */
class Recommendation extends Model
{
    /** @use HasFactory<RecommendationFactory> */
    use HasFactory;

    protected $fillable = [
        'user_id',
        'product_id',
        'cluster',
        'score',
        'computed_at',
    ];

    protected function casts(): array
    {
        return [
            'cluster' => 'integer',
            'score' => 'float',
            'computed_at' => 'datetime',
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
