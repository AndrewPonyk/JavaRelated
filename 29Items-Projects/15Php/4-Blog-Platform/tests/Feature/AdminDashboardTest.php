<?php

declare(strict_types=1);

namespace Tests\Feature;

use App\Livewire\Admin\Dashboard;
use App\Models\Category;
use App\Models\Post;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Livewire\Livewire;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

class AdminDashboardTest extends TestCase
{
    use RefreshDatabase;

    #[Test]
    public function it_shows_content_counts_and_recent_posts(): void
    {
        Category::factory()->count(2)->create();
        Post::factory()->count(3)->create(['title' => 'Published One']);
        Post::factory()->draft()->create(['title' => 'A Draft']);

        Livewire::actingAs(User::factory()->create())
            ->test(Dashboard::class)
            ->assertOk()
            ->assertSee('Published')
            ->assertSee('Drafts')
            ->assertSee('Categories')
            ->assertSee('Published One')
            ->assertSee('A Draft');
    }

    #[Test]
    public function guests_cannot_reach_the_dashboard(): void
    {
        $this->get(route('admin.dashboard'))->assertRedirect(route('login'));
    }
}
