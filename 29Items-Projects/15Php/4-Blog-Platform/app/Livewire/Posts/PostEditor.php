<?php

declare(strict_types=1);

namespace App\Livewire\Posts;

use App\Models\Post;
use App\Models\Tag;
use App\Services\MarkdownService;
use App\Services\ReadingTimeService;
use Illuminate\Contracts\View\View;
use Illuminate\Foundation\Auth\Access\AuthorizesRequests;
use Illuminate\Support\Carbon;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
use Livewire\Attributes\Computed;
use Livewire\Attributes\Validate;
use Livewire\Component;

/**
 * Authenticated create/update editor with a LIVE Markdown preview.
 *
 * The preview is computed server-side via MarkdownService (the same sanitiser
 * used on save), so what the author sees is exactly what visitors get. The
 * preview pane is driven by `wire:model.live.debounce` on the body field —
 * tune the debounce to balance freshness vs. request volume.
 *
 * On save: render body → HTML, compute reading time, sync tags, redirect.
 */
class PostEditor extends Component
{
    use AuthorizesRequests;

    public ?Post $post = null;

    #[Validate('required|string|min:3|max:255')]
    public string $title = '';

    #[Validate('nullable|string|max:255')]
    public string $slug = '';

    #[Validate('nullable|string|max:300')]
    public string $excerpt = '';

    #[Validate('required|string|min:10')]
    public string $body = '';

    #[Validate('nullable|integer|exists:categories,id')]
    public ?int $categoryId = null;

    /** @var list<string> */
    #[Validate('array')]
    public array $tags = [];

    #[Validate('required|in:draft,published')]
    public string $status = Post::STATUS_DRAFT;

    /**
     * Optional publish timestamp. A future value schedules the post: the
     * `published()` query scope hides it until the date passes.
     */
    #[Validate('nullable|date')]
    public ?string $publishedAt = null;

    /**
     * Hydrate the form when editing an existing post.
     */
    public function mount(?Post $post = null): void
    {
        if ($post?->exists) {
            $this->authorize('update', $post);

            $this->post = $post;
            $this->fill([
                'title' => $post->title,
                'slug' => $post->slug,
                'excerpt' => (string) $post->excerpt,
                'body' => $post->body,
                'categoryId' => $post->category_id,
                'status' => $post->status,
                'publishedAt' => $post->published_at?->format('Y-m-d\TH:i'),
                'tags' => $post->loadMissing('tags')->tags->pluck('name')->all(),
            ]);
        } else {
            $this->authorize('create', Post::class);
        }
    }

    /**
     * Unique-slug rule needs to ignore the current row on update.
     *
     * @return array<string, mixed>
     */
    protected function rules(): array
    {
        return [
            'slug' => [
                'nullable', 'string', 'max:255',
                Rule::unique('posts', 'slug')->ignore($this->post?->id),
            ],
        ];
    }

    public function save(MarkdownService $markdown, ReadingTimeService $readingTime): void
    {
        $this->authorize($this->post ? 'update' : 'create', $this->post ?? Post::class);

        $this->validate();

        $slug = $this->slug !== '' ? Str::slug($this->slug) : Str::slug($this->title);
        $bodyHtml = $markdown->toHtml($this->body);
        $plain = $markdown->toPlainText($this->body);
        $imageCount = substr_count($bodyHtml, '<img');

        // Resolve the publish timestamp: an explicit (possibly future) date wins
        // and schedules the post; otherwise preserve the existing date or use now.
        $publishedAt = null;
        if ($this->status === Post::STATUS_PUBLISHED) {
            $publishedAt = $this->publishedAt !== null && $this->publishedAt !== ''
                ? Carbon::parse($this->publishedAt)
                : ($this->post?->published_at ?? now());
        }

        $attributes = [
            'title' => $this->title,
            'slug' => $slug,
            'excerpt' => $this->excerpt !== '' ? $this->excerpt : Str::limit($plain, 160),
            'body' => $this->body,
            'body_html' => $bodyHtml,
            'reading_time' => $readingTime->minutes($plain, $imageCount),
            'status' => $this->status,
            'category_id' => $this->categoryId,
            'published_at' => $publishedAt,
            'user_id' => $this->post?->user_id ?? auth()->id(),
        ];

        $post = $this->post
            ? tap($this->post)->update($attributes)
            : Post::create($attributes);

        $this->syncTags($post);

        // The PostObserver busts the related-posts cache on save.

        session()->flash('status', 'Post saved.');
        $this->redirectRoute('admin.posts', navigate: true);
    }

    /**
     * Live, sanitised preview used by the editor's right-hand pane.
     * Accessed in Blade as `$this->previewHtml` (Livewire 3 computed property).
     *
     * Computed properties are read as properties, so Livewire can't method-inject
     * dependencies here — resolve MarkdownService from the container instead.
     */
    #[Computed]
    public function previewHtml(): string
    {
        return app(MarkdownService::class)->toHtml($this->body);
    }

    public function render(): View
    {
        return view('livewire.posts.post-editor');
    }

    private function syncTags(Post $post): void
    {
        $tagIds = collect($this->tags)
            ->map(fn (string $name): string => trim($name))
            ->filter()
            ->unique()
            ->map(fn (string $name) => Tag::firstOrCreate(
                ['slug' => Str::slug($name)],
                ['name' => $name],
            )->id)
            ->all();

        $post->tags()->sync($tagIds);
    }
}
