<div>
    <div class="mb-8 flex items-center justify-between">
        <h1 class="text-2xl font-bold">Dashboard</h1>
        <a href="{{ route('admin.posts.create') }}" wire:navigate
           class="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700 dark:bg-white dark:text-gray-900">
            + New post
        </a>
    </div>

    <div class="grid grid-cols-2 gap-4 sm:grid-cols-4">
        @foreach ([
            ['Published', $publishedCount],
            ['Drafts', $draftCount],
            ['Categories', $categoryCount],
            ['Tags', $tagCount],
        ] as [$label, $value])
            <div class="rounded-lg border border-gray-100 p-4 dark:border-gray-800">
                <div class="text-3xl font-bold">{{ $value }}</div>
                <div class="text-sm text-gray-500">{{ $label }}</div>
            </div>
        @endforeach
    </div>

    <h2 class="mt-10 mb-3 text-lg font-semibold">Recent posts</h2>
    <ul class="divide-y divide-gray-100 dark:divide-gray-800">
        @forelse ($recent as $post)
            <li wire:key="recent-{{ $post->id }}" class="flex items-center justify-between py-3">
                <a href="{{ route('admin.posts.edit', $post) }}" wire:navigate class="hover:underline">
                    {{ $post->title }}
                </a>
                <span @class([
                    'rounded px-2 py-0.5 text-xs',
                    'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300' => $post->status === \App\Models\Post::STATUS_PUBLISHED,
                    'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300' => $post->status !== \App\Models\Post::STATUS_PUBLISHED,
                ])>{{ $post->status }}</span>
            </li>
        @empty
            <li class="py-3 text-gray-500">No posts yet. <a href="{{ route('admin.posts.create') }}" wire:navigate class="underline">Write one</a>.</li>
        @endforelse
    </ul>
</div>
