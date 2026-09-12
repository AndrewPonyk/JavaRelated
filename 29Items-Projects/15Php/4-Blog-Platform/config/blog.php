<?php

declare(strict_types=1);

return [

    /*
    |--------------------------------------------------------------------------
    | Reading time
    |--------------------------------------------------------------------------
    | Average adult silent reading speed in words per minute. Used by
    | App\Services\ReadingTimeService. 200–265 is typical; 225 is a safe default.
    */
    'words_per_minute' => (int) env('BLOG_WORDS_PER_MINUTE', 225),

    /*
    |--------------------------------------------------------------------------
    | Related posts (TF-IDF)
    |--------------------------------------------------------------------------
    | How many related articles to surface, and how long to cache the computed
    | similarity results (seconds). See App\Services\RelatedPostsService.
    */
    'related_posts_count' => (int) env('BLOG_RELATED_POSTS_COUNT', 3),
    'related_cache_ttl' => (int) env('BLOG_RELATED_CACHE_TTL', 86400),

    /*
    |--------------------------------------------------------------------------
    | Excerpts & meta descriptions
    |--------------------------------------------------------------------------
    | Character budget for auto-generated excerpts and SEO meta descriptions.
    */
    'excerpt_length' => (int) env('BLOG_EXCERPT_LENGTH', 160),

    /*
    |--------------------------------------------------------------------------
    | Default social-share (Open Graph) image
    |--------------------------------------------------------------------------
    | Used when a post has no explicit og_image. Path is relative to the web root.
    */
    'default_og_image' => env('BLOG_DEFAULT_OG_IMAGE', '/images/og-default.svg'),

];
