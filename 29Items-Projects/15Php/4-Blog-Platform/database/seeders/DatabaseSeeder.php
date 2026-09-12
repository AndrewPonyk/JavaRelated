<?php

declare(strict_types=1);

namespace Database\Seeders;

use App\Models\Category;
use App\Models\Post;
use App\Models\Tag;
use App\Models\User;
use Illuminate\Database\Seeder;
use Illuminate\Support\Str;

class DatabaseSeeder extends Seeder
{
    public function run(): void
    {
        // The single author/admin (matches the editor's auth()->id() ?? 1 fallback).
        $author = User::factory()->create([
            'name' => 'Andrii',
            'email' => 'author@example.com',
        ]);

        $categories = Category::factory()
            ->count(4)
            ->sequence(
                ['name' => 'Engineering', 'slug' => 'engineering'],
                ['name' => 'Product', 'slug' => 'product'],
                ['name' => 'Design', 'slug' => 'design'],
                ['name' => 'Personal', 'slug' => 'personal'],
            )
            ->create();

        $tags = collect(['php', 'laravel', 'livewire', 'tailwind', 'seo', 'algorithms'])
            ->map(fn (string $name) => Tag::create(['name' => $name, 'slug' => Str::slug($name)]));

        // 24 published posts spread across categories, each with a few tags.
        Post::factory()
            ->count(24)
            ->recycle($author)
            ->recycle($categories)
            ->create()
            ->each(fn (Post $post) => $post->tags()->attach(
                $tags->random(random_int(2, 4))->pluck('id')->all()
            ));

        // A couple of drafts to exercise the draft/published filtering.
        Post::factory()->count(3)->draft()->recycle($author)->recycle($categories)->create();
    }
}
