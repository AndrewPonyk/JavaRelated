<?php

namespace App\Providers;

use App\Events\ProductPurchased;
use App\Listeners\RecordPurchaseForRecommendations;
use Illuminate\Foundation\Support\Providers\EventServiceProvider as ServiceProvider;

/**
 * Maps domain events to their listeners. Adding a new reaction to an event
 * is a one-line change here — the emitter never needs to know.
 */
class EventServiceProvider extends ServiceProvider
{
    /**
     * @var array<class-string, array<int, class-string>>
     */
    protected $listen = [
        ProductPurchased::class => [
            RecordPurchaseForRecommendations::class,
            // e.g. NotifySellerOfSale::class,
            // e.g. DecrementInventory::class,
        ],
    ];

    public function shouldDiscoverEvents(): bool
    {
        return false;
    }
}
