<?php

namespace App\Listeners;

use App\Events\ProductPurchased;
use App\Jobs\RebuildRecommendations;
use App\Models\UserEvent;
use App\Notifications\ProductPurchasedNotification;
use Illuminate\Contracts\Queue\ShouldQueue;
use Illuminate\Queue\InteractsWithQueue;

/**
 * Queued listener (ShouldQueue) — runs on a worker, off the request thread.
 * Records the purchase as behavioral telemetry and notifies the buyer.
 * The expensive cluster recompute is debounced to a scheduled job rather
 * than run per-purchase (see TECH-NOTES pitfall #10).
 */
class RecordPurchaseForRecommendations implements ShouldQueue
{
    use InteractsWithQueue;

    public int $tries = 3;

    public int $backoff = 10;

    public function handle(ProductPurchased $event): void
    {
        // 1. Append high-signal telemetry for the recommendation engine.
        UserEvent::create([
            'user_id' => $event->buyer->id,
            'product_id' => $event->product->id,
            'type' => UserEvent::TYPE_PURCHASE,
            'weight' => UserEvent::WEIGHTS[UserEvent::TYPE_PURCHASE],
            'context' => ['quantity' => $event->quantity],
        ]);

        // 2. Notify the buyer (queued notification channel).
        $event->buyer->notify(new ProductPurchasedNotification($event->product));

        // 3. Debounced rebuild of recommendations. ShouldBeUnique coalesces a
        //    burst of purchases into a single clustering pass.
        RebuildRecommendations::dispatch();
    }

    /**
     * Failed after all retries → log/alert. The job lands in failed_jobs.
     */
    public function failed(ProductPurchased $event, \Throwable $e): void
    {
        logger()->error('Failed to record purchase telemetry', [
            'user_id' => $event->buyer->id,
            'product_id' => $event->product->id,
            'exception' => $e->getMessage(),
        ]);
    }
}
