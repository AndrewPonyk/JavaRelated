<?php

declare(strict_types=1);

namespace App\Console\Commands;

use App\Models\Post;
use App\Services\RelatedPostsService;
use Illuminate\Console\Command;

/**
 * Pre-computes the TF-IDF related-posts cache for every published post so the
 * first visitor after a deploy/publish never pays the computation cost.
 * Scheduled nightly (see routes/console.php).
 */
class WarmRelatedPostsCache extends Command
{
    protected $signature = 'blog:warm-related';

    protected $description = 'Pre-compute and cache TF-IDF related posts for all published posts';

    public function handle(RelatedPostsService $related): int
    {
        $posts = Post::query()->published()->get();

        if ($posts->isEmpty()) {
            $this->info('No published posts to warm.');

            return self::SUCCESS;
        }

        // Start from a clean version so stale entries don't linger.
        $related->flush();

        $bar = $this->output->createProgressBar($posts->count());
        $bar->start();

        foreach ($posts as $post) {
            $related->relatedTo($post);
            $bar->advance();
        }

        $bar->finish();
        $this->newLine();
        $this->info("Warmed related-posts cache for {$posts->count()} posts.");

        return self::SUCCESS;
    }
}
