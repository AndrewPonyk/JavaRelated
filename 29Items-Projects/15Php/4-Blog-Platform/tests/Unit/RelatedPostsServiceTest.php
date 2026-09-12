<?php

declare(strict_types=1);

namespace Tests\Unit;

use App\Models\Post;
use App\Services\RelatedPostsService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

/**
 * Integration-flavoured test for the TF-IDF recommender. We assert RANKING on a
 * small, controlled corpus rather than exact float scores (which are brittle).
 */
class RelatedPostsServiceTest extends TestCase
{
    use RefreshDatabase;

    #[Test]
    public function it_ranks_topically_similar_posts_higher(): void
    {
        $target = $this->publishedPost(
            'Building reactive UIs with Laravel Livewire',
            'Laravel Livewire lets you build reactive interfaces using PHP components without writing JavaScript.',
        );

        $veryRelated = $this->publishedPost(
            'Laravel Livewire component patterns',
            'Reactive Livewire components in Laravel keep PHP and the interface in sync for dynamic interfaces.',
        );

        $somewhatRelated = $this->publishedPost(
            'Getting started with PHP',
            'PHP is a popular backend programming language used to build web applications and components.',
        );

        $unrelated = $this->publishedPost(
            'Growing tomatoes in summer',
            'Tomatoes need rich soil, plenty of sunlight, and regular watering to thrive in the garden.',
        );

        $related = app(RelatedPostsService::class)->relatedTo($target, limit: 3);

        // The most topically-overlapping post should rank first.
        $this->assertSame($veryRelated->id, $related->first()->id);

        // The gardening post shares no vocabulary → must not appear.
        $this->assertFalse($related->contains('id', $unrelated->id));

        // The PHP post shares some vocabulary → should be included.
        $this->assertTrue($related->contains('id', $somewhatRelated->id));
    }

    #[Test]
    public function it_excludes_the_target_post_itself(): void
    {
        $target = $this->publishedPost('Unique topic', 'Some content about a unique topic here.');
        $this->publishedPost('Another topic', 'Different content about another unique topic here.');

        $related = app(RelatedPostsService::class)->relatedTo($target);

        $this->assertFalse($related->contains('id', $target->id));
    }

    #[Test]
    public function it_returns_empty_when_there_is_no_corpus(): void
    {
        $target = $this->publishedPost('Lonely post', 'The only post in the database has no peers.');

        $this->assertTrue(app(RelatedPostsService::class)->relatedTo($target)->isEmpty());
    }

    private function publishedPost(string $title, string $body): Post
    {
        return Post::factory()->create([
            'title' => $title,
            'body' => $body,
            'status' => Post::STATUS_PUBLISHED,
            'published_at' => now()->subDay(),
        ]);
    }
}
