<?php

declare(strict_types=1);

namespace App\Livewire\Admin;

use App\Models\Category;
use App\Models\Post;
use App\Models\Tag;
use Illuminate\Contracts\View\View;
use Livewire\Component;

/**
 * Admin landing page: at-a-glance counts and quick links.
 */
class Dashboard extends Component
{
    public function render(): View
    {
        return view('livewire.admin.dashboard', [
            'publishedCount' => Post::query()->published()->count(),
            'draftCount' => Post::query()->where('status', Post::STATUS_DRAFT)->count(),
            'categoryCount' => Category::query()->count(),
            'tagCount' => Tag::query()->count(),
            'recent' => Post::query()->latest()->limit(5)->get(['id', 'title', 'slug', 'status']),
        ]);
    }
}
