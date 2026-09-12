<?php

declare(strict_types=1);

namespace Tests\Feature;

use App\Models\Post;
use Illuminate\Foundation\Testing\RefreshDatabase;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

/**
 * Machine-facing endpoints: sitemap, robots, RSS feed, and health check.
 */
class PublicPagesTest extends TestCase
{
    use RefreshDatabase;

    #[Test]
    public function the_sitemap_lists_published_posts(): void
    {
        $published = Post::factory()->create();
        $draft = Post::factory()->draft()->create();

        $response = $this->get('/sitemap.xml');

        $response->assertOk()
            ->assertHeader('Content-Type', 'application/xml; charset=UTF-8')
            ->assertSee($published->slug, false)
            ->assertDontSee($draft->slug, false);
    }

    #[Test]
    public function robots_txt_is_served_and_blocks_admin(): void
    {
        $this->get('/robots.txt')
            ->assertOk()
            ->assertSee('Disallow: /admin')
            ->assertSee('Sitemap:');
    }

    #[Test]
    public function the_rss_feed_renders_published_posts(): void
    {
        $post = Post::factory()->create(['title' => 'Feed Item Title']);

        $this->get('/feed')
            ->assertOk()
            ->assertHeader('Content-Type', 'application/xml; charset=UTF-8')
            ->assertSee('Feed Item Title');
    }

    #[Test]
    public function the_health_check_endpoint_responds(): void
    {
        $this->get('/up')->assertOk();
    }

    #[Test]
    public function security_headers_are_present(): void
    {
        $this->get('/')
            ->assertHeader('X-Content-Type-Options', 'nosniff')
            ->assertHeader('X-Frame-Options', 'SAMEORIGIN');
    }
}
