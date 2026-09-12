<?php

declare(strict_types=1);

namespace App\Http\Controllers;

use App\Models\Post;
use Illuminate\Http\Request;
use Illuminate\Http\Response;

/**
 * Thin controller for non-Livewire, machine-facing endpoints (feeds, sitemap).
 *
 * The human-facing read/list/edit pages are full-page Livewire components
 * (see App\Livewire\Posts\*). This controller covers the bits that are plain
 * HTTP responses rather than interactive UI.
 */
class PostController extends Controller
{
    /**
     * RSS 2.0 feed of the most recent published posts.
     */
    public function feed(Request $request): Response
    {
        $posts = Post::query()
            ->published()
            ->with('author:id,name')
            ->latest('published_at')
            ->limit(20)
            ->get();

        return response()
            ->view('feed.rss', ['posts' => $posts])
            ->header('Content-Type', 'application/xml; charset=UTF-8');
    }
}
