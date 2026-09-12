<?php

declare(strict_types=1);

namespace Tests\Feature;

use App\Livewire\Admin\PostManager;
use App\Livewire\Posts\PostEditor;
use App\Models\Post;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Livewire\Livewire;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

/**
 * Authenticated admin flows: creating/editing posts via the editor and managing
 * them (publish toggle, delete) via PostManager. Also guards the admin area.
 */
class AdminPostManagementTest extends TestCase
{
    use RefreshDatabase;

    private function admin(): User
    {
        return User::factory()->create();
    }

    #[Test]
    public function guests_are_redirected_from_admin_pages(): void
    {
        $this->get(route('admin.dashboard'))->assertRedirect(route('login'));
        $this->get(route('admin.posts'))->assertRedirect(route('login'));
        $this->get(route('admin.posts.create'))->assertRedirect(route('login'));
    }

    #[Test]
    public function the_editor_creates_a_post_renders_markdown_and_computes_reading_time(): void
    {
        Livewire::actingAs($this->admin())
            ->test(PostEditor::class)
            ->set('title', 'My First Post')
            ->set('body', "# Hello\n\nThis is **bold** markdown content for the post body.")
            ->set('status', Post::STATUS_PUBLISHED)
            ->call('save')
            ->assertHasNoErrors()
            ->assertRedirect(route('admin.posts'));

        $post = Post::firstWhere('title', 'My First Post');

        $this->assertNotNull($post);
        $this->assertSame(Post::STATUS_PUBLISHED, $post->status);
        $this->assertStringContainsString('<strong>bold</strong>', (string) $post->body_html);
        $this->assertGreaterThanOrEqual(1, $post->reading_time);
        $this->assertNotNull($post->published_at);
        $this->assertSame('my-first-post', $post->slug);
    }

    #[Test]
    public function the_editor_validates_required_fields(): void
    {
        Livewire::actingAs($this->admin())
            ->test(PostEditor::class)
            ->set('title', '')
            ->set('body', 'short')
            ->call('save')
            ->assertHasErrors(['title' => 'required', 'body' => 'min']);
    }

    #[Test]
    public function markdown_rendering_strips_dangerous_html(): void
    {
        Livewire::actingAs($this->admin())
            ->test(PostEditor::class)
            ->set('title', 'XSS Attempt')
            ->set('body', "Hello <script>alert('xss')</script> world")
            ->set('status', Post::STATUS_PUBLISHED)
            ->call('save')
            ->assertHasNoErrors();

        $post = Post::firstWhere('title', 'XSS Attempt');

        $this->assertStringNotContainsString('<script>', (string) $post->body_html);
    }

    #[Test]
    public function the_editor_updates_an_existing_post_and_syncs_tags(): void
    {
        $post = Post::factory()->create(['title' => 'Original']);

        Livewire::actingAs($this->admin())
            ->test(PostEditor::class, ['post' => $post])
            ->set('title', 'Updated Title')
            ->set('tags', ['php', 'laravel'])
            ->call('save')
            ->assertHasNoErrors();

        $post->refresh();
        $this->assertSame('Updated Title', $post->title);
        $this->assertEqualsCanonicalizing(['php', 'laravel'], $post->load('tags')->tags->pluck('name')->all());
    }

    #[Test]
    public function publishing_with_a_future_date_schedules_the_post(): void
    {
        Livewire::actingAs($this->admin())
            ->test(PostEditor::class)
            ->set('title', 'Scheduled Post')
            ->set('body', 'This content is scheduled for the future.')
            ->set('status', Post::STATUS_PUBLISHED)
            ->set('publishedAt', now()->addWeek()->format('Y-m-d\TH:i'))
            ->call('save')
            ->assertHasNoErrors();

        $post = Post::firstWhere('title', 'Scheduled Post');

        $this->assertNotNull($post);
        $this->assertSame(Post::STATUS_PUBLISHED, $post->status);
        $this->assertTrue($post->published_at->isFuture());
        // Hidden by the published() scope until the date passes.
        $this->assertFalse(Post::query()->published()->whereKey($post->id)->exists());
    }

    #[Test]
    public function the_manager_toggles_publish_state(): void
    {
        $post = Post::factory()->draft()->create();

        Livewire::actingAs($this->admin())
            ->test(PostManager::class)
            ->call('togglePublish', $post->id);

        $this->assertSame(Post::STATUS_PUBLISHED, $post->fresh()->status);
        $this->assertNotNull($post->fresh()->published_at);
    }

    #[Test]
    public function the_manager_deletes_a_post(): void
    {
        $post = Post::factory()->create();

        Livewire::actingAs($this->admin())
            ->test(PostManager::class)
            ->call('delete', $post->id);

        $this->assertDatabaseMissing('posts', ['id' => $post->id]);
    }
}
