<?php

declare(strict_types=1);

namespace App\Livewire\Admin;

use App\Models\Post;
use Illuminate\Contracts\View\View;
use Illuminate\Foundation\Auth\Access\AuthorizesRequests;
use Illuminate\Pagination\LengthAwarePaginator;
use Livewire\Attributes\Url;
use Livewire\Component;
use Livewire\WithPagination;

/**
 * Admin post index: list every post (draft + published), search, toggle publish
 * state, and delete. Create/edit are handled by the PostEditor component.
 */
class PostManager extends Component
{
    use AuthorizesRequests;
    use WithPagination;

    #[Url(as: 'q')]
    public string $search = '';

    #[Url]
    public string $status = '';

    public function updatingSearch(): void
    {
        $this->resetPage();
    }

    /**
     * Flip a post between draft and published.
     */
    public function togglePublish(int $postId): void
    {
        $post = Post::findOrFail($postId);
        $this->authorize('update', $post);

        if ($post->status === Post::STATUS_PUBLISHED) {
            $post->update(['status' => Post::STATUS_DRAFT]);
        } else {
            $post->update([
                'status' => Post::STATUS_PUBLISHED,
                'published_at' => $post->published_at ?? now(),
            ]);
        }

        session()->flash('status', "“{$post->title}” is now {$post->status}.");
    }

    public function delete(int $postId): void
    {
        $post = Post::findOrFail($postId);
        $this->authorize('delete', $post);

        $title = $post->title;
        $post->delete();

        session()->flash('status', "“{$title}” deleted.");
    }

    public function render(): View
    {
        return view('livewire.admin.post-manager', [
            'posts' => $this->posts(),
        ]);
    }

    /**
     * @return LengthAwarePaginator<Post>
     */
    private function posts(): LengthAwarePaginator
    {
        return Post::query()
            ->search($this->search)
            ->when($this->status !== '', fn ($q) => $q->where('status', $this->status))
            ->with(['category:id,name', 'author:id,name'])
            ->latest()
            ->paginate(15);
    }
}
