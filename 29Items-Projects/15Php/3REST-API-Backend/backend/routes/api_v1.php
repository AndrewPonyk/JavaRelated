<?php

use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\OrderController;
use App\Http\Controllers\Api\V1\ProductController;
use App\Http\Controllers\Api\V1\RecommendationController;
use App\Http\Controllers\Api\V1\UserEventController;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| API v1 Routes
|--------------------------------------------------------------------------
| Rate limiting is applied per route group via named limiters defined in
| App\Providers\AppServiceProvider (see configureRateLimiting()):
|   - 'auth' : strict, anti-brute-force (e.g. 5/min by IP)
|   - 'api'  : per-authenticated-user default (e.g. 60/min)
*/

// ── Public / auth endpoints (strict throttle) ─────────────────────────────
Route::middleware('throttle:auth')->group(function () {
    Route::post('auth/register', [AuthController::class, 'register'])->name('auth.register');
    Route::post('auth/login', [AuthController::class, 'login'])->name('auth.login');
});

// ── Authenticated endpoints (per-user throttle) ───────────────────────────
Route::middleware(['auth:sanctum', 'throttle:api'])->group(function () {
    Route::post('auth/logout', [AuthController::class, 'logout'])->name('auth.logout');
    Route::get('auth/me', [AuthController::class, 'me'])->name('auth.me');

    // Product catalog. apiResource omits create/edit (no server-rendered forms).
    Route::apiResource('products', ProductController::class);

    // Checkout & order history.
    Route::get('orders', [OrderController::class, 'index'])->name('orders.index');
    Route::post('orders', [OrderController::class, 'store'])->name('orders.store');
    Route::get('orders/{order}', [OrderController::class, 'show'])->name('orders.show');

    // Behavioral telemetry → feeds the recommendation engine.
    Route::post('events', [UserEventController::class, 'store'])->name('events.store');

    // Personalized recommendations (cached read of precomputed data).
    Route::get('recommendations', [RecommendationController::class, 'index'])->name('recommendations.index');
});
