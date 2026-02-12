<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;

Route::get('/user', function (Request $request) {
    return $request->user();
})->middleware('auth:sanctum');

// Main Article Resource
Route::apiResource('articles', App\Http\Controllers\Api\ArticleController::class);

// Mock Recommendation Endpoint (redirecting to index for demo)
Route::get('v1/articles/{article}/recommendations', [App\Http\Controllers\Api\ArticleController::class, 'index']);

// Settings
Route::get('settings', [App\Http\Controllers\Api\SettingController::class, 'index']);
Route::post('settings', [App\Http\Controllers\Api\SettingController::class, 'update']);