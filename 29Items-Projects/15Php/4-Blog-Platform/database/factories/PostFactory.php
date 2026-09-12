<?php

declare(strict_types=1);

namespace Database\Factories;

use App\Models\Category;
use App\Models\Post;
use App\Models\User;
use App\Services\MarkdownService;
use App\Services\ReadingTimeService;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Post>
 */
class PostFactory extends Factory
{
    protected $model = Post::class;

    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $title = rtrim($this->faker->sentence(random_int(4, 8)), '.');
        $markdown = $this->markdownBody();

        // Render through the real services so seeded data mirrors production.
        $html = app(MarkdownService::class)->toHtml($markdown);
        $plain = app(MarkdownService::class)->toPlainText($markdown);
        $readingTime = app(ReadingTimeService::class)->minutes($plain);

        return [
            'user_id' => User::factory(),
            'category_id' => Category::factory(),
            'title' => $title,
            // Lowercase suffix keeps demo slugs URL-clean (matches editor output).
            'slug' => Str::slug($title).'-'.Str::lower(Str::random(6)),
            'excerpt' => Str::limit($plain, 160),
            'body' => $markdown,
            'body_html' => $html,
            'reading_time' => $readingTime,
            'status' => Post::STATUS_PUBLISHED,
            'published_at' => $this->faker->dateTimeBetween('-1 year', 'now'),
        ];
    }

    public function draft(): static
    {
        return $this->state(fn (): array => [
            'status' => Post::STATUS_DRAFT,
            'published_at' => null,
        ]);
    }

    private function markdownBody(): string
    {
        $paragraphs = collect(range(1, random_int(3, 6)))
            ->map(fn (): string => $this->faker->paragraph(random_int(4, 8)))
            ->implode("\n\n");

        return "## {$this->faker->sentence(3)}\n\n{$paragraphs}\n\n"
            ."- {$this->faker->words(3, true)}\n- {$this->faker->words(3, true)}\n\n"
            ."> {$this->faker->sentence()}";
    }
}
