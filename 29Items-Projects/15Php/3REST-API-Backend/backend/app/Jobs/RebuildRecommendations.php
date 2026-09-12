<?php

namespace App\Jobs;

use App\Services\Recommendation\RecommendationService;
use Illuminate\Bus\Queueable;
use Illuminate\Contracts\Queue\ShouldBeUnique;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Foundation\Bus\Dispatchable;
use Illuminate\Queue\InteractsWithQueue;
use Illuminate\Queue\SerializesModels;

/**
 * Recomputes the recommendation set for all users. Implemented as
 * ShouldBeUnique so a burst of purchases coalesces into a single rebuild
 * within the dedup window — clustering stays off the request path
 * (docs/TECH-NOTES.md pitfall #10).
 */
class RebuildRecommendations implements ShouldBeUnique, ShouldQueue
{
    use Dispatchable, InteractsWithQueue, Queueable, SerializesModels;

    /** Dedup window in seconds: at most one rebuild per 5 minutes. */
    public int $uniqueFor = 300;

    public function uniqueId(): string
    {
        return 'rebuild-recommendations';
    }

    public function handle(RecommendationService $service): void
    {
        $service->rebuildAll();
    }
}
