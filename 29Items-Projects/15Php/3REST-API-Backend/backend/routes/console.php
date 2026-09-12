<?php

use Illuminate\Foundation\Inspiring;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Schedule;

Artisan::command('inspire', function () {
    $this->comment(Inspiring::quote());
})->purpose('Display an inspiring quote');

/*
|--------------------------------------------------------------------------
| Scheduled tasks
|--------------------------------------------------------------------------
| Nightly recompute of behavioral clusters → recommendations (Phase 3).
| Runs off-peak so clustering cost never touches the request path
| (docs/TECH-NOTES.md pitfall #10). The `scheduler` container in
| docker-compose.yml invokes `schedule:run` every minute.
*/
Schedule::command('recommendations:recompute')
    ->dailyAt('02:00')
    ->withoutOverlapping();
