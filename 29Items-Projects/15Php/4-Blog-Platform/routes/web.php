<?php

declare(strict_types=1);

use App\Http\Controllers\Auth\LogoutController;
use App\Http\Controllers\PostController;
use App\Http\Controllers\SitemapController;
use App\Livewire\Admin\CategoryManager;
use App\Livewire\Admin\Dashboard;
use App\Livewire\Admin\PostManager;
use App\Livewire\Auth\Login;
use App\Livewire\Posts\PostEditor;
use App\Livewire\Posts\PostList;
use App\Livewire\Posts\PostShow;
use Illuminate\Support\Facades\Route;

/*
|--------------------------------------------------------------------------
| Public routes (full-page Livewire components)
|--------------------------------------------------------------------------
*/
Route::get('/', PostList::class)->name('home');
Route::get('/blog/{post}', PostShow::class)->name('posts.show');

/*
|--------------------------------------------------------------------------
| Machine-facing endpoints (SEO)
|--------------------------------------------------------------------------
*/
Route::get('/sitemap.xml', [SitemapController::class, 'index'])->name('sitemap');
Route::get('/feed', [PostController::class, 'feed'])->name('feed');
Route::get('/robots.txt', [SitemapController::class, 'robots'])->name('robots');

/*
|--------------------------------------------------------------------------
| Guest (auth) routes
|--------------------------------------------------------------------------
*/
Route::middleware('guest')->group(function (): void {
    Route::get('/login', Login::class)->name('login');
});

Route::post('/logout', LogoutController::class)->middleware('auth')->name('logout');

/*
|--------------------------------------------------------------------------
| Authenticated author / admin routes
|--------------------------------------------------------------------------
*/
Route::middleware('auth')->prefix('admin')->name('admin.')->group(function (): void {
    Route::get('/', Dashboard::class)->name('dashboard');

    Route::get('/posts', PostManager::class)->name('posts');
    Route::get('/posts/create', PostEditor::class)->name('posts.create');
    Route::get('/posts/{post}/edit', PostEditor::class)->name('posts.edit');

    Route::get('/categories', CategoryManager::class)->name('categories');
});
