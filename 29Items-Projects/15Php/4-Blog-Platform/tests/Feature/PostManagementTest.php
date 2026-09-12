<?php

declare(strict_types=1);

namespace Tests\Feature;

use App\Livewire\Posts\PostList;
use App\Models\Category;
use App\Models\Post;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Livewire\Livewire;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

/**
 * Public-facing post flows: listing, search, reading a post, and draft hiding.
 */
class PostManagementTest extends TestCase
{
    use RefreshDatabase;

    #[Test]
    public function the_home_page_lists_published_posts_only(): void
    {
        Post::factory()->create(['title' => 'Visible Post']);
        Post::factory()->draft()->create(['title' => 'Hidden Draft']);

        Livewire::test(PostList::class)
            ->assertSee('Visible Post')
            ->assertDontSee('Hidden Draft');
    }

    #[Test]
    public function search_filters_the_post_list(): void
    {
        Post::factory()->create(['title' => 'Laravel Livewire Guide']);
        Post::factory()->create(['title' => 'Cooking Pasta']);

        Livewire::test(PostList::class)
            ->set('search', 'Livewire')
            ->assertSee('Laravel Livewire Guide')
            ->assertDontSee('Cooking Pasta');
    }

    #[Test]
    public function the_list_can_be_filtered_by_category(): void
    {
        $tech = Category::factory()->create(['name' => 'Tech', 'slug' => 'tech']);
        Post::factory()->create(['title' => 'Tech Post', 'category_id' => $tech->id]);
        Post::factory()->create(['title' => 'Food Post']);

        Livewire::test(PostList::class)
            ->set('categorySlug', 'tech')
            ->assertSee('Tech Post')
            ->assertDontSee('Food Post');
    }

    #[Test]
    public function a_visitor_can_read_a_published_post(): void
    {
        $post = Post::factory()->create(['title' => 'Readable Article', 'reading_time' => 5]);

        $this->get(route('posts.show', $post))
            ->assertOk()
            ->assertSee('Readable Article')
            ->assertSee('5 min read');
    }

    #[Test]
    public function a_draft_post_returns_404_for_guests(): void
    {
        $draft = Post::factory()->draft()->create();

        $this->get(route('posts.show', $draft))->assertNotFound();
    }

    #[Test]
    public function an_authenticated_author_may_preview_a_draft(): void
    {
        $draft = Post::factory()->draft()->create(['title' => 'Secret Draft']);

        $this->actingAs(User::factory()->create())
            ->get(route('posts.show', $draft))
            ->assertOk()
            ->assertSee('Secret Draft');
    }

    #[Test]
    public function the_post_page_exposes_seo_metadata(): void
    {
        $post = Post::factory()->create(['title' => 'SEO Friendly Title']);

        $response = $this->get(route('posts.show', $post));

        $response->assertSee('<title>SEO Friendly Title</title>', false);
        $response->assertSee('property="og:title"', false);
        $response->assertSee('application/ld+json', false);
    }

    #[Test]
    public function json_ld_cannot_break_out_of_its_script_tag(): void
    {
        $post = Post::factory()->create([
            'title' => 'Evil </script><script>alert(1)</script>',
        ]);

        // With JSON_HEX_TAG the angle brackets are <-escaped, so the raw
        // breakout sequence must never appear in the rendered HTML.
        $this->get(route('posts.show', $post))
            ->assertOk()
            ->assertDontSee('</script><script>alert(1)', false);
    }
}
