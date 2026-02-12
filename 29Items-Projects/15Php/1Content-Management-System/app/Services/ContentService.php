<?php

namespace App\Services;

use App\Models\Article;
use App\Models\User;
use Illuminate\Support\Str;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Cache;
use Exception;

class ContentService
{
    /**
     * Create a new article with versioning and search indexing.
     *
     * @param array $data Validated input data
     * @param User $user The author creating the content
     * @return Article
     * @throws Exception
     */
    public function createArticle(array $data, User $user): Article
    {
        return DB::transaction(function () use ($data, $user) {
            // Ensure unique slug logic if needed (e.g. append count)
            // Storing basic article info
            $article = new Article();
            $article->fill($data);
            $article->author_id = $user->id;
            // $article->category_id = $data['category_id'] ?? null; // fill() handles this if in $data
            $article->version = 1;

            if (isset($data['meta'])) {
                $article->meta = $data['meta'];
            }
            
            if (($data['status'] ?? 'draft') === 'published') {
                $article->published_at = now();
            }

            $article->save();

            // Handle Tags (Many-to-Many)
            if (isset($data['tags']) && is_array($data['tags'])) {
                $article->tags()->sync($data['tags']);
            }

            // Create initial revision logic would go here
            // e.g. ArticleRevision::create([...]);

            // Dispatch events for Search Indexing (Elasticsearch) logic
            // event(new ArticleCreated($article));

            return $article;
        });
    }

    /**
     * Update an existing article, handling version bumps.
     *
     * @param Article $article
     * @param array $data
     * @return Article
     */
    public function updateArticle(Article $article, array $data): Article
    {
        return DB::transaction(function () use ($article, $data) {
            // Save current state as a revision before modifying?
            // This is a simplified "overwrite" approach for the stub
            
            $article->fill($data);

            if ($article->isDirty('content') || $article->isDirty('title')) {
                $article->version++;
            }

            if ($article->isDirty('status') && $data['status'] === 'published' && !$article->published_at) {
                $article->published_at = now();
            }
            
            $article->save();

            // Invalidate cache
            Cache::forget("article_{$article->slug}");

            return $article;
        });
    }

    /**
     * Retrieve an article by slug, using cache.
     * 
     * @param string $slug
     * @return Article|null
     */
    public function getArticleBySlug(string $slug): ?Article
    {
        return Cache::remember("article_{$slug}", 600, function () use ($slug) {
            return Article::with(['author', 'tags', 'category'])
                ->where('slug', $slug)
                ->where('status', 'published')
                ->first(); // or firstOrFail() in controller
        });
    }
}
