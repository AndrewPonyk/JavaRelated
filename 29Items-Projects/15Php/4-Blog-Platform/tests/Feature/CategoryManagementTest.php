<?php

declare(strict_types=1);

namespace Tests\Feature;

use App\Livewire\Admin\CategoryManager;
use App\Models\Category;
use App\Models\Post;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Livewire\Livewire;
use PHPUnit\Framework\Attributes\Test;
use Tests\TestCase;

class CategoryManagementTest extends TestCase
{
    use RefreshDatabase;

    private function admin(): User
    {
        return User::factory()->create();
    }

    #[Test]
    public function it_creates_a_category_with_a_slug(): void
    {
        Livewire::actingAs($this->admin())
            ->test(CategoryManager::class)
            ->set('name', 'Software Engineering')
            ->set('description', 'Posts about building software.')
            ->call('save')
            ->assertHasNoErrors();

        $this->assertDatabaseHas('categories', [
            'name' => 'Software Engineering',
            'slug' => 'software-engineering',
        ]);
    }

    #[Test]
    public function category_names_must_be_unique(): void
    {
        Category::factory()->create(['name' => 'Tech']);

        Livewire::actingAs($this->admin())
            ->test(CategoryManager::class)
            ->set('name', 'Tech')
            ->call('save')
            ->assertHasErrors(['name' => 'unique']);
    }

    #[Test]
    public function it_edits_an_existing_category(): void
    {
        $category = Category::factory()->create(['name' => 'Old Name']);

        Livewire::actingAs($this->admin())
            ->test(CategoryManager::class)
            ->call('edit', $category->id)
            ->set('name', 'New Name')
            ->call('save')
            ->assertHasNoErrors();

        $this->assertSame('New Name', $category->fresh()->name);
        $this->assertSame('new-name', $category->fresh()->slug);
    }

    #[Test]
    public function deleting_a_category_nulls_its_posts_category(): void
    {
        $category = Category::factory()->create();
        $post = Post::factory()->create(['category_id' => $category->id]);

        Livewire::actingAs($this->admin())
            ->test(CategoryManager::class)
            ->call('delete', $category->id);

        $this->assertDatabaseMissing('categories', ['id' => $category->id]);
        $this->assertNull($post->fresh()->category_id);
    }

    #[Test]
    public function guests_cannot_reach_the_category_manager(): void
    {
        $this->get(route('admin.categories'))->assertRedirect(route('login'));
    }
}
