<div>
    <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-bold">Posts</h1>
        <a href="{{ route('admin.posts.create') }}" wire:navigate
           class="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700 dark:bg-white dark:text-gray-900">
            + New post
        </a>
    </div>

    <div class="mb-4 flex flex-col gap-3 sm:flex-row">
        <input type="search" wire:model.live.debounce.400ms="search" placeholder="Search posts…"
               class="w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800">
        <select wire:model.live="status" class="rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800">
            <option value="">All statuses</option>
            <option value="published">Published</option>
            <option value="draft">Draft</option>
        </select>
    </div>

    <div class="overflow-x-auto rounded-lg border border-gray-100 dark:border-gray-800" wire:loading.class="opacity-50">
        <table class="w-full text-left text-sm">
            <thead class="border-b border-gray-100 text-gray-500 dark:border-gray-800">
                <tr>
                    <th class="px-4 py-3">Title</th>
                    <th class="px-4 py-3">Category</th>
                    <th class="px-4 py-3">Status</th>
                    <th class="px-4 py-3 text-right">Actions</th>
                </tr>
            </thead>
            <tbody class="divide-y divide-gray-100 dark:divide-gray-800">
                @forelse ($posts as $post)
                    <tr wire:key="post-{{ $post->id }}">
                        <td class="px-4 py-3 font-medium">{{ $post->title }}</td>
                        <td class="px-4 py-3 text-gray-500">{{ $post->category?->name ?? '—' }}</td>
                        <td class="px-4 py-3">
                            <button type="button" wire:click="togglePublish({{ $post->id }})"
                                    @class([
                                        'rounded px-2 py-0.5 text-xs',
                                        'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300' => $post->status === \App\Models\Post::STATUS_PUBLISHED,
                                        'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300' => $post->status !== \App\Models\Post::STATUS_PUBLISHED,
                                    ])>
                                {{ $post->status }}
                            </button>
                        </td>
                        <td class="px-4 py-3 text-right">
                            <a href="{{ route('admin.posts.edit', $post) }}" wire:navigate class="text-blue-600 hover:underline">Edit</a>
                            <button type="button"
                                    wire:click="delete({{ $post->id }})"
                                    wire:confirm="Delete “{{ $post->title }}”? This cannot be undone."
                                    class="ml-3 text-red-600 hover:underline">
                                Delete
                            </button>
                        </td>
                    </tr>
                @empty
                    <tr><td colspan="4" class="px-4 py-8 text-center text-gray-500">No posts found.</td></tr>
                @endforelse
            </tbody>
        </table>
    </div>

    <div class="mt-4">{{ $posts->links() }}</div>
</div>
