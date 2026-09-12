<?php

declare(strict_types=1);

namespace App\Services\Seo;

use App\Models\Post;
use Illuminate\Support\Str;

/**
 * Builds SEO metadata for pages: title, meta description, canonical URL,
 * Open Graph, Twitter cards, and JSON-LD structured data.
 *
 * Returns a plain data array consumed by the layout's <head> (see
 * resources/views/layouts/app.blade.php). Keeping it framework-light makes the
 * output easy to assert in tests.
 */
class SeoService
{
    /**
     * @return array<string, mixed>
     */
    public function forPost(Post $post): array
    {
        // Author is used in OG/JSON-LD; ensure it's available without tripping
        // the lazy-loading guard when a caller didn't eager-load it.
        $post->loadMissing('author');

        $title = $post->meta_title ?: $post->title;
        $description = $post->metaDescription();
        $url = url("/blog/{$post->slug}");
        $image = $post->og_image ? url($post->og_image) : $this->defaultImage();

        return [
            'title' => $title,
            'description' => $description,
            'canonical' => $url,
            'robots' => $post->isPublished() ? 'index,follow' : 'noindex,nofollow',
            'og' => [
                'og:type' => 'article',
                'og:title' => $title,
                'og:description' => $description,
                'og:url' => $url,
                'og:image' => $image,
                'article:published_time' => $post->published_at?->toIso8601String(),
                'article:author' => $post->author?->name,
            ],
            'twitter' => [
                'twitter:card' => 'summary_large_image',
                'twitter:title' => $title,
                'twitter:description' => $description,
                'twitter:image' => $image,
            ],
            'jsonLd' => $this->articleJsonLd($post, $title, $description, $url, $image),
        ];
    }

    /**
     * Generic metadata for non-article pages (home, listings).
     *
     * @return array<string, mixed>
     */
    public function forPage(string $title, ?string $description = null): array
    {
        $description ??= (string) config('app.name');

        return [
            'title' => $title,
            'description' => Str::limit($description, (int) config('blog.excerpt_length', 160)),
            'canonical' => url()->current(),
            'robots' => 'index,follow',
            'og' => [
                'og:type' => 'website',
                'og:title' => $title,
                'og:description' => $description,
                'og:url' => url()->current(),
                'og:image' => $this->defaultImage(),
            ],
            'twitter' => ['twitter:card' => 'summary'],
            'jsonLd' => null,
        ];
    }

    /**
     * schema.org/BlogPosting structured data (rendered as <script type="application/ld+json">).
     *
     * @return array<string, mixed>
     */
    private function articleJsonLd(Post $post, string $title, string $description, string $url, string $image): array
    {
        return [
            '@context' => 'https://schema.org',
            '@type' => 'BlogPosting',
            'headline' => $title,
            'description' => $description,
            'image' => $image,
            'url' => $url,
            'datePublished' => $post->published_at?->toIso8601String(),
            'dateModified' => $post->updated_at?->toIso8601String(),
            'author' => [
                '@type' => 'Person',
                'name' => $post->author?->name ?? config('app.name'),
            ],
            'publisher' => [
                '@type' => 'Organization',
                'name' => config('app.name'),
            ],
            'mainEntityOfPage' => ['@type' => 'WebPage', '@id' => $url],
        ];
    }

    private function defaultImage(): string
    {
        // Branded social-share fallback; override per-environment via config/blog.php.
        return url(config('blog.default_og_image', '/images/og-default.svg'));
    }
}
