<?php

declare(strict_types=1);

namespace Tests\Unit;

use App\Models\Post;
use App\Services\Seo\SeoService;
use Illuminate\Foundation\Testing\RefreshDatabase;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

class SeoServiceTest extends TestCase
{
    use RefreshDatabase;

    #[Test]
    public function it_builds_a_full_metadata_payload_for_a_post(): void
    {
        $post = Post::factory()->create(['title' => 'A Great Article']);

        $seo = app(SeoService::class)->forPost($post);

        $this->assertSame('A Great Article', $seo['title']);
        $this->assertSame('index,follow', $seo['robots']);
        $this->assertStringContainsString($post->slug, $seo['canonical']);
        $this->assertSame('article', $seo['og']['og:type']);
        $this->assertSame('BlogPosting', $seo['jsonLd']['@type']);
        $this->assertSame('A Great Article', $seo['jsonLd']['headline']);
    }

    #[Test]
    public function unpublished_posts_are_marked_noindex(): void
    {
        $post = Post::factory()->draft()->create();

        $seo = app(SeoService::class)->forPost($post);

        $this->assertSame('noindex,nofollow', $seo['robots']);
    }

    #[Test]
    public function it_uses_explicit_meta_overrides_when_present(): void
    {
        $post = Post::factory()->create([
            'meta_title' => 'Custom SEO Title',
            'meta_description' => 'A hand-written description.',
        ]);

        $seo = app(SeoService::class)->forPost($post);

        $this->assertSame('Custom SEO Title', $seo['title']);
        $this->assertSame('A hand-written description.', $seo['description']);
    }

    #[Test]
    public function it_builds_generic_metadata_for_pages(): void
    {
        $seo = app(SeoService::class)->forPage('All Articles');

        $this->assertSame('All Articles', $seo['title']);
        $this->assertSame('website', $seo['og']['og:type']);
        $this->assertNull($seo['jsonLd']);
    }
}
