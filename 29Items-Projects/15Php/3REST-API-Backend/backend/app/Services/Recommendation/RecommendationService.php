<?php

namespace App\Services\Recommendation;

use App\Models\Product;
use App\Models\Recommendation;
use App\Models\User;
use App\Models\UserEvent;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\DB;

/**
 * Turns raw behavioral telemetry into precomputed, per-user recommendations:
 *
 *   user_events ─▶ feature vectors ─▶ k-means clusters ─▶ cluster affinity
 *               ─▶ top-N products per user ─▶ recommendations table
 *
 * `rebuildAll()` is the (async/scheduled) write path; `forUser()` is the cheap
 * cached read path used by the API. See docs/ARCHITECTURE.md §2.3.2–2.3.3.
 */
class RecommendationService
{
    private const CACHE_TTL = 3600;

    private const CACHE_TAG = 'recommendations';

    public function __construct(private readonly BehaviorClusteringService $clustering) {}

    /**
     * Recompute clusters and recommendations for every user with telemetry.
     *
     * @return int number of users processed
     */
    public function rebuildAll(): int
    {
        $vectors = $this->buildUserVectors();
        if ($vectors === []) {
            return 0;
        }

        $assignments = $this->clustering->cluster($vectors);

        // Aggregate each cluster's category affinity from its members' vectors.
        $clusterProfiles = [];
        foreach ($vectors as $userId => $vector) {
            $cluster = $assignments[$userId];
            foreach ($vector as $category => $weight) {
                $clusterProfiles[$cluster][$category] = ($clusterProfiles[$cluster][$category] ?? 0.0) + $weight;
            }
        }

        // Catalog grouped by category (active products only).
        $productsByCategory = Product::query()->active()
            ->get(['id', 'category'])
            ->groupBy('category');

        // Products each user already purchased → excluded from their recs.
        $purchasedByUser = UserEvent::query()
            ->where('type', UserEvent::TYPE_PURCHASE)
            ->get(['user_id', 'product_id'])
            ->groupBy('user_id')
            ->map(fn ($rows) => $rows->pluck('product_id')->all());

        $topN = (int) config('api.recommendation.top_n', 10);
        $processed = 0;

        DB::transaction(function () use (
            $vectors, $assignments, $clusterProfiles, $productsByCategory, $purchasedByUser, $topN, &$processed
        ) {
            $now = now();

            foreach ($vectors as $userId => $vector) {
                $cluster = $assignments[$userId];
                $profile = $clusterProfiles[$cluster] ?? [];
                arsort($profile); // strongest category affinities first

                $excluded = $purchasedByUser[$userId] ?? [];
                $candidates = [];

                foreach ($profile as $category => $affinity) {
                    foreach (($productsByCategory[$category] ?? []) as $product) {
                        if (in_array($product->id, $excluded, true)) {
                            continue;
                        }
                        $candidates[$product->id] = max($candidates[$product->id] ?? 0.0, (float) $affinity);
                    }
                }

                arsort($candidates);
                $top = array_slice($candidates, 0, $topN, true);

                // Atomically replace this user's recommendation set.
                Recommendation::where('user_id', $userId)->delete();

                $rows = [];
                foreach ($top as $productId => $score) {
                    $rows[] = [
                        'user_id' => $userId,
                        'product_id' => $productId,
                        'cluster' => $cluster,
                        'score' => round($score, 4),
                        'computed_at' => $now,
                        'created_at' => $now,
                        'updated_at' => $now,
                    ];
                }

                if ($rows !== []) {
                    Recommendation::insert($rows);
                }

                $processed++;
            }
        });

        $this->flushCache();

        return $processed;
    }

    /**
     * Aggregate user_events into per-user, per-category weighted feature vectors.
     *
     * @return array<int, array<string, float>> user id → {category => weight}
     */
    public function buildUserVectors(): array
    {
        $rows = UserEvent::query()
            ->join('products', 'user_events.product_id', '=', 'products.id')
            ->groupBy('user_events.user_id', 'products.category')
            ->select(
                'user_events.user_id as user_id',
                'products.category as category',
                DB::raw('SUM(user_events.weight) as total_weight'),
            )
            ->get();

        $vectors = [];
        foreach ($rows as $row) {
            $vectors[(int) $row->user_id][$row->category] = (float) $row->total_weight;
        }

        return $vectors;
    }

    /**
     * Cheap, cached read path for the API. Falls back to popular products for
     * cold-start users with no computed recommendations yet.
     *
     * @return Collection<int, Recommendation>
     */
    public function forUser(User $user, ?int $limit = null): Collection
    {
        $limit ??= (int) config('api.recommendation.top_n', 10);

        return Cache::tags([self::CACHE_TAG])->remember(
            "recommendations:user:{$user->id}:{$limit}",
            self::CACHE_TTL,
            function () use ($user, $limit) {
                $recs = Recommendation::query()
                    ->where('user_id', $user->id)
                    ->with('product')
                    ->orderByDesc('score')
                    ->limit($limit)
                    ->get();

                return $recs->isNotEmpty() ? $recs : $this->popularFallback($limit);
            },
        );
    }

    /**
     * Cold-start: most-purchased active products (newest as a tiebreaker),
     * wrapped as Recommendation models for a uniform response shape.
     *
     * @return Collection<int, Recommendation>
     */
    private function popularFallback(int $limit): Collection
    {
        $popularIds = UserEvent::query()
            ->where('type', UserEvent::TYPE_PURCHASE)
            ->select('product_id', DB::raw('COUNT(*) as c'))
            ->groupBy('product_id')
            ->orderByDesc('c')
            ->limit($limit)
            ->pluck('product_id');

        $products = Product::query()->active()
            ->when($popularIds->isNotEmpty(), fn ($q) => $q->whereIn('id', $popularIds))
            ->latest()
            ->limit($limit)
            ->get();

        return $products->map(function (Product $product) {
            $rec = new Recommendation([
                'product_id' => $product->id,
                'cluster' => -1, // sentinel: cold-start fallback
                'score' => 0.0,
                'computed_at' => now(),
            ]);
            $rec->setRelation('product', $product);

            return $rec;
        });
    }

    private function flushCache(): void
    {
        Cache::tags([self::CACHE_TAG])->flush();
    }
}
