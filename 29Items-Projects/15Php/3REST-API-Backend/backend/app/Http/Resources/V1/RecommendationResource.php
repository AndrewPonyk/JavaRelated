<?php

namespace App\Http\Resources\V1;

use Illuminate\Http\Request;
use Illuminate\Http\Resources\Json\JsonResource;

/**
 * Wraps a precomputed (or cold-start fallback) recommendation. The nested
 * product is always eager-loaded by RecommendationService.
 */
class RecommendationResource extends JsonResource
{
    /**
     * @return array<string, mixed>
     */
    public function toArray(Request $request): array
    {
        return [
            'product_id' => $this->product_id,
            'score' => round((float) $this->score, 4),
            'cluster' => $this->cluster,
            'reason' => $this->cluster === -1 ? 'popular' : 'behavioral_cluster',
            'product' => new ProductResource($this->whenLoaded('product')),
        ];
    }
}
