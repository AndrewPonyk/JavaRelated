<?php

namespace App\Console\Commands;

use App\Services\Recommendation\RecommendationService;
use Illuminate\Console\Command;

/**
 * Nightly (scheduled) full recompute of behavioral clusters → recommendations.
 * Registered in routes/console.php. Runs the clustering synchronously here
 * because it executes off-peak in the scheduler container, not in a request.
 */
class RecomputeRecommendations extends Command
{
    protected $signature = 'recommendations:recompute';

    protected $description = 'Recompute user behavior clusters and rebuild recommendations';

    public function handle(RecommendationService $service): int
    {
        $this->info('Rebuilding recommendations…');

        $count = $service->rebuildAll();

        $this->info("Done. Recommendations rebuilt for {$count} user(s).");

        return self::SUCCESS;
    }
}
