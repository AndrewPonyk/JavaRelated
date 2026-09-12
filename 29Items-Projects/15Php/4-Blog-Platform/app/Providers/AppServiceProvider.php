<?php

declare(strict_types=1);

namespace App\Providers;

use App\Services\ReadingTimeService;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register container bindings.
     */
    public function register(): void
    {
        // Inject the configured words-per-minute into the reading-time estimator
        // so the value is environment-tunable (config/blog.php → .env).
        $this->app->bind(ReadingTimeService::class, function (): ReadingTimeService {
            return new ReadingTimeService(
                wordsPerMinute: (int) config('blog.words_per_minute', 225),
            );
        });

        // MarkdownService and SeoService are auto-resolved (no constructor deps
        // beyond what the container already knows); RelatedPostsService receives
        // MarkdownService + the default cache repository automatically.
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        // Fail loudly on accidental lazy-loading (N+1) outside production.
        Model::preventLazyLoading(!$this->app->isProduction());

        // Make every model immutable-by-default against unguarded mass-assignment
        // surprises is handled per-model via $fillable; nothing to do here yet.
    }
}
