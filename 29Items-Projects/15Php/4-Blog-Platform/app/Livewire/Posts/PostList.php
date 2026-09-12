<?php

declare(strict_types=1);

namespace App\Livewire\Posts;

use App\Models\Category;
use App\Models\Post;
use App\Services\Seo\SeoService;
use Illuminate\Contracts\View\View;
use Illuminate\Pagination\LengthAwarePaginator;
use Livewire\Attributes\Url;
use Livewire\Component;
use Livewire\WithPagination;

/**
 * Public, paginated, searchable post index.
 *
 * Search + filters are bound to the query string (`#[Url]`) so results are
 * shareable and survive a refresh. `wire:model.live.debounce` on the search box
 * keeps the network chatter sane (see the Blade template).
 */
class PostList extends Component
{
    use WithPagination;

    #[Url(as: 'q')]
    public string $search = '';

    #[Url(as: 'category')]
    public string $categorySlug = '';

    /** Reset pagination whenever a filter changes. */
    public function updating(string $name): void
    {
        if (in_array($name, ['search', 'categorySlug'], true)) {
            $this->resetPage();
        }
    }

    public function render(SeoService $seo): View
    {
        return view('livewire.posts.post-list', [
            'posts' => $this->posts(),
            'categories' => Category::query()->orderBy('name')->get(['name', 'slug']),
            'seo' => $seo->forPage(config('app.name').' — Articles'),
        ]);
    }

    /**
     * @return LengthAwarePaginator<Post>
     */
    private function posts(): LengthAwarePaginator
    {
        return Post::query()
            ->published()
            ->search($this->search)
            ->when($this->categorySlug, fn ($q) => $q->whereHas(
                'category',
                fn ($c) => $c->where('slug', $this->categorySlug)
            ))
            ->with(['category:id,name,slug', 'author:id,name']) // avoid N+1
            ->latest('published_at')
            ->paginate(9);
    }
}
