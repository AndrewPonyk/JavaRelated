<?php

declare(strict_types=1);

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function (): void {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

/*
|--------------------------------------------------------------------------
| Scheduled tasks
|--------------------------------------------------------------------------
| Warm the TF-IDF related-posts cache nightly so the first visitor after a
| publish never pays the computation cost. The sitemap and feed are generated
| on-the-fly by their controllers, so they need no scheduled regeneration.
*/
Schedule::command('blog:warm-related')
    ->dailyAt('03:00')
    ->withoutOverlapping()
    ->onOneServer();
