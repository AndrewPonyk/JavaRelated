<?php

declare(strict_types=1);

namespace App\Livewire\Posts;

use App\Exceptions\PostNotPublishedException;
use App\Models\Post;
use App\Services\RelatedPostsService;
use App\Services\Seo\SeoService;
use Illuminate\Contracts\View\View;
use Illuminate\Support\Collection;
use Livewire\Attributes\Locked;
use Livewire\Component;

/**
 * Renders a single published post: sanitised body HTML, reading-time badge,
 * SEO metadata, and TF-IDF related posts.
 *
 * Related posts are a non-blocking enhancement — if the recommender or cache
 * fails, the article still renders (graceful degradation, see ARCHITECTURE §2.6).
 */
class PostShow extends Component
{
    #[Locked]
    public Post $post;

    /**
     * Route-model bound by slug (see Post::getRouteKeyName()).
     */
    public function mount(Post $post): void
    {
        // Authenticated authors may preview their own drafts; the public cannot.
        if (!$post->isPublished() && !auth()->check()) {
            throw PostNotPublishedException::forSlug($post->slug);
        }

        $this->post = $post->load(['author:id,name', 'category:id,name,slug', 'tags:id,name,slug']);
    }

    public function render(RelatedPostsService $related, SeoService $seo): View
    {
        return view('livewire.posts.post-show', [
            'related' => $this->relatedPosts($related),
            'seo' => $seo->forPost($this->post),
        ]);
    }

    /**
     * @return Collection<int, Post>
     */
    private function relatedPosts(RelatedPostsService $related): Collection
    {
        try {
            return $related->relatedTo($this->post);
        } catch (\Throwable $e) {
            report($e); // log + continue; related posts must never 500 the article

            return collect();
        }
    }
}
