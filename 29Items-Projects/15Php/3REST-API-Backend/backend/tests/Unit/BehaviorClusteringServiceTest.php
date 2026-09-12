<?php

namespace Tests\Unit;

use App\Services\Recommendation\BehaviorClusteringService;
use PHPUnit\Framework\TestCase;

/**
 * Pure unit tests for the clustering algorithm — no framework, no I/O.
 */
class BehaviorClusteringServiceTest extends TestCase
{
    public function test_euclidean_distance_is_correct(): void
    {
        $service = new BehaviorClusteringService;

        // 3-4-5 right triangle.
        $this->assertSame(5.0, $service->distance([0.0, 0.0], [3.0, 4.0]));
        $this->assertSame(0.0, $service->distance([1.0, 2.0], [1.0, 2.0]));
    }

    public function test_nearest_centroid_picks_the_closest(): void
    {
        $service = new BehaviorClusteringService;

        $centroids = [
            0 => [0.0, 0.0],
            1 => [10.0, 10.0],
        ];

        $this->assertSame(0, $service->nearestCentroid([1.0, 1.0], $centroids));
        $this->assertSame(1, $service->nearestCentroid([9.0, 8.0], $centroids));
    }

    public function test_two_separated_behavior_groups_land_in_distinct_clusters(): void
    {
        // k=2 so we expect exactly two groups to emerge.
        $service = new BehaviorClusteringService(clusters: 2);

        // Users 1-2 love 'electronics'; users 3-4 love 'books'.
        $vectors = [
            1 => ['electronics' => 5.0, 'books' => 0.0],
            2 => ['electronics' => 4.0, 'books' => 1.0],
            3 => ['electronics' => 0.0, 'books' => 5.0],
            4 => ['electronics' => 1.0, 'books' => 4.0],
        ];

        $assignments = $service->cluster($vectors);

        // Same-interest users share a cluster...
        $this->assertSame($assignments[1], $assignments[2]);
        $this->assertSame($assignments[3], $assignments[4]);

        // ...and the two groups are in different clusters.
        $this->assertNotSame($assignments[1], $assignments[3]);
    }

    public function test_empty_input_yields_no_assignments(): void
    {
        $this->assertSame([], (new BehaviorClusteringService)->cluster([]));
    }
}
