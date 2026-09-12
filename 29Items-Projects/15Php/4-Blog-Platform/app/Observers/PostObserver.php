<?php

declare(strict_types=1);

namespace App\Observers;

use App\Models\Post;
use App\Services\RelatedPostsService;

/**
 * Keeps the TF-IDF related-posts cache coherent: any time a post is created,
 * updated, or deleted the whole corpus shifts, so we bump the cache version
 * (see RelatedPostsService::flush()).
 */
class PostObserver
{
    public function __construct(private readonly RelatedPostsService $related) {}

    public function saved(Post $post): void
    {
        $this->related->flush();
    }

    public function deleted(Post $post): void
    {
        $this->related->flush();
    }
}
