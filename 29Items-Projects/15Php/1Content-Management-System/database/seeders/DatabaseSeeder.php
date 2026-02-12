<?php

namespace Database\Seeders;

use App\Models\User;
use App\Models\Article; // Add Article Model
use Illuminate\Database\Seeder;
use Illuminate\Support\Facades\Hash;

class DatabaseSeeder extends Seeder
{
    /**
     * Seed the application's database.
     */
    public function run(): void
    {
        // 1. Create a User (Author)
        $user = User::create([
            'name' => 'Admin User',
            'email' => 'admin@example.com',
            'password' => Hash::make('password'),
        ]);

        // 2. Create a Dummy Article (ID 1)
        Article::create([
            'id' => 1, // Explicitly set ID
            'title' => 'Welcome to the Enterprise CMS',
            'slug' => 'welcome-to-cms',
            'excerpt' => 'This is the first article in the system.',
            'content' => '<h1>Hello World!</h1><p>Our new CMS is up and running.</p>',
            'status' => 'published',
            'author_id' => $user->id,
            'published_at' => now(),
            'version' => 1,
            'meta' => ['seo_title' => 'Welcome'],
        ]);
        
        // 3. Create some more dummy articles for the "Recommendations" list
        Article::create([
            'title' => 'Running Laravel on Docker',
            'slug' => 'running-laravel-docker',
            'excerpt' => 'A guide to containerizing PHP applications.',
            'content' => 'Docker is awesome...',
            'status' => 'published',
            'author_id' => $user->id,
            'published_at' => now(),
        ]);

        Article::create([
            'title' => 'React Integration with Vite',
            'slug' => 'react-vite-integration',
            'excerpt' => 'How to use Vite for lightning fast HMR.',
            'content' => 'Vite is fast...',
            'status' => 'published',
            'author_id' => $user->id,
            'published_at' => now(),
        ]);
    }
}
