<?php

namespace App\Providers;

use App\Repositories\Contracts\ProductRepositoryInterface;
use App\Repositories\Eloquent\ProductRepository;
use App\Services\Recommendation\BehaviorClusteringService;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\ServiceProvider;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Bind interfaces → concrete implementations (Dependency Inversion).
     */
    public function register(): void
    {
        $this->app->bind(ProductRepositoryInterface::class, ProductRepository::class);

        // Inject the configured k (cluster count) into the clustering engine.
        $this->app->bind(BehaviorClusteringService::class, fn () => new BehaviorClusteringService(
            clusters: (int) config('api.recommendation.clusters', 8),
        ));
    }

    public function boot(): void
    {
        $this->configureRateLimiting();

        // Surface N+1s loudly outside production (see TECH-NOTES pitfall #1).
        Model::preventLazyLoading(! $this->app->isProduction());
    }

    /**
     * Named rate limiters referenced by routes (throttle:api, throttle:auth).
     * Limits are backed by Redis in prod so they are shared across instances
     * (see TECH-NOTES pitfall #5).
     */
    private function configureRateLimiting(): void
    {
        // Per-authenticated-user default for the API.
        RateLimiter::for('api', function (Request $request) {
            $perMinute = (int) config('api.rate_limit_per_minute', 60);

            return $request->user()
                ? Limit::perMinute($perMinute)->by('user:'.$request->user()->id)
                : Limit::perMinute(30)->by('ip:'.$request->ip());
        });

        // Strict anti-brute-force limiter for credential endpoints.
        RateLimiter::for('auth', function (Request $request) {
            $perMinute = (int) config('api.auth_rate_limit', 5);

            return Limit::perMinute($perMinute)
                ->by('auth:'.$request->ip())
                ->response(fn () => response()->json([
                    'message' => 'Too many attempts. Please slow down.',
                    'error_code' => 'RATE_LIMITED',
                ], 429));
        });
    }
}
