<?php

use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| API Routes (entry point)
|--------------------------------------------------------------------------
| URI-based versioning: each version is an isolated route file mounted under
| its own prefix. Adding /v2 later never touches the v1 contract.
| See docs/ARCHITECTURE.md §2.1.
*/

Route::prefix('v1')
    ->name('api.v1.')
    ->group(base_path('routes/api_v1.php'));

// Future:
// Route::prefix('v2')->name('api.v2.')->group(base_path('routes/api_v2.php'));
