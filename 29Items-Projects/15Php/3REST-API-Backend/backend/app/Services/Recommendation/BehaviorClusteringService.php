<?php

namespace App\Services\Recommendation;

/**
 * User-behavior clustering for personalization & recommendations.
 *
 * Pipeline (see docs/ARCHITECTURE.md §2.3.2):
 *   1. Aggregate raw UserEvent rows into a per-user feature vector
 *      (weighted affinity per product category).
 *   2. Group users into K behavioral clusters via k-means.
 *   3. For a given user, recommend the most popular categories/products
 *      among their cluster peers that they haven't engaged with yet.
 *
 * This class is intentionally pure / dependency-light so the algorithm is
 * unit-testable in isolation (BehaviorClusteringServiceTest). Persistence
 * and event sourcing are wired by the caller (a queued listener / scheduled
 * command), not here.
 */
class BehaviorClusteringService
{
    public function __construct(
        private readonly int $clusters = 8,
        private readonly int $maxIterations = 50,
    ) {}

    /**
     * Run k-means over the supplied user feature vectors.
     *
     * @param  array<int, array<string, float>>  $userVectors  keyed by user id → {feature => value}
     * @return array<int, int> user id → cluster index
     */
    public function cluster(array $userVectors): array
    {
        if ($userVectors === []) {
            return [];
        }

        $features = $this->featureSpace($userVectors);
        $points = $this->densify($userVectors, $features);

        $k = min($this->clusters, count($points));
        $centroids = $this->initialCentroids($points, $k);

        $assignments = [];
        for ($iteration = 0; $iteration < $this->maxIterations; $iteration++) {
            $next = [];
            foreach ($points as $userId => $vector) {
                $next[$userId] = $this->nearestCentroid($vector, $centroids);
            }

            if ($next === $assignments) {
                break; // converged — assignments stable
            }

            $assignments = $next;
            $centroids = $this->recomputeCentroids($points, $assignments, $centroids);
        }

        return $assignments;
    }

    /**
     * Euclidean distance between two equal-length dense vectors.
     *
     * @param  array<int, float>  $a
     * @param  array<int, float>  $b
     */
    public function distance(array $a, array $b): float
    {
        $sum = 0.0;
        foreach ($a as $i => $value) {
            $delta = $value - ($b[$i] ?? 0.0);
            $sum += $delta * $delta;
        }

        return sqrt($sum);
    }

    /**
     * Index of the nearest centroid for a point.
     *
     * @param  array<int, float>  $vector
     * @param  array<int, array<int, float>>  $centroids
     */
    public function nearestCentroid(array $vector, array $centroids): int
    {
        $best = 0;
        $bestDistance = INF;

        foreach ($centroids as $index => $centroid) {
            $d = $this->distance($vector, $centroid);
            if ($d < $bestDistance) {
                $bestDistance = $d;
                $best = $index;
            }
        }

        return $best;
    }

    /**
     * Collect the sorted union of all feature keys → a stable column order.
     *
     * @param  array<int, array<string, float>>  $userVectors
     * @return array<int, string>
     */
    private function featureSpace(array $userVectors): array
    {
        $features = [];
        foreach ($userVectors as $vector) {
            foreach (array_keys($vector) as $feature) {
                $features[$feature] = true;
            }
        }
        $keys = array_keys($features);
        sort($keys);

        return $keys;
    }

    /**
     * Project sparse {feature=>value} maps onto a dense, ordered vector.
     *
     * @param  array<int, array<string, float>>  $userVectors
     * @param  array<int, string>  $features
     * @return array<int, array<int, float>>
     */
    private function densify(array $userVectors, array $features): array
    {
        $dense = [];
        foreach ($userVectors as $userId => $vector) {
            $dense[$userId] = array_map(
                fn (string $feature) => (float) ($vector[$feature] ?? 0.0),
                $features,
            );
        }

        return $dense;
    }

    /**
     * Deterministic seeding: first K distinct points become initial centroids.
     * (Production could swap in k-means++ for better separation.)
     *
     * @param  array<int, array<int, float>>  $points
     * @return array<int, array<int, float>>
     */
    private function initialCentroids(array $points, int $k): array
    {
        return array_slice(array_values($points), 0, $k);
    }

    /**
     * Mean of all points assigned to each cluster. Empty clusters keep their
     * previous centroid to avoid NaNs.
     *
     * @param  array<int, array<int, float>>  $points
     * @param  array<int, int>  $assignments
     * @param  array<int, array<int, float>>  $previous
     * @return array<int, array<int, float>>
     */
    private function recomputeCentroids(array $points, array $assignments, array $previous): array
    {
        $sums = [];
        $counts = [];

        foreach ($assignments as $userId => $cluster) {
            foreach ($points[$userId] as $i => $value) {
                $sums[$cluster][$i] = ($sums[$cluster][$i] ?? 0.0) + $value;
            }
            $counts[$cluster] = ($counts[$cluster] ?? 0) + 1;
        }

        $centroids = $previous;
        foreach ($sums as $cluster => $vector) {
            $centroids[$cluster] = array_map(
                fn (float $total) => $total / $counts[$cluster],
                $vector,
            );
        }

        return $centroids;
    }
}
