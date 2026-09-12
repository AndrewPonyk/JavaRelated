<?php

declare(strict_types=1);

namespace Tests\Feature;

use App\Models\Post;
use Illuminate\Foundation\Testing\RefreshDatabase;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

class WarmRelatedPostsCacheTest extends TestCase
{
    use RefreshDatabase;

    #[Test]
    public function it_warms_the_cache_for_published_posts(): void
    {
        Post::factory()->count(3)->create();

        $this->artisan('blog:warm-related')
            ->assertSuccessful();
    }

    #[Test]
    public function it_handles_an_empty_corpus_gracefully(): void
    {
        $this->artisan('blog:warm-related')
            ->expectsOutputToContain('No published posts')
            ->assertSuccessful();
    }
}
