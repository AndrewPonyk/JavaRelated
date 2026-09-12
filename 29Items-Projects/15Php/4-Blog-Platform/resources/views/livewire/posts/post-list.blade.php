<div>
    @push('head')
        <x-seo :seo="$seo" />
    @endpush

    <header class="mb-8">
        <h1 class="text-3xl font-bold tracking-tight">Articles</h1>
        <p class="mt-2 text-gray-500">Thoughts on engineering, product, and design.</p>
    </header>

    {{-- Filters: search debounced to avoid a request per keystroke. --}}
    <div class="mb-8 flex flex-col gap-3 sm:flex-row">
        <input
            type="search"
            wire:model.live.debounce.400ms="search"
            placeholder="Search articles…"
            class="w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800"
            aria-label="Search articles"
        >
        <select
            wire:model.live="categorySlug"
            class="rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800"
            aria-label="Filter by category"
        >
            <option value="">All categories</option>
            @foreach ($categories as $category)
                <option value="{{ $category->slug }}">{{ $category->name }}</option>
            @endforeach
        </select>
    </div>

    {{-- Results --}}
    <div wire:loading.class="opacity-50" class="space-y-8">
        @forelse ($posts as $post)
            <article wire:key="post-{{ $post->id }}" class="group">
                <a href="{{ route('posts.show', $post) }}" wire:navigate class="block">
                    <div class="flex items-center gap-2 text-xs text-gray-500">
                        @if ($post->category)
                            <span class="rounded bg-gray-100 px-2 py-0.5 dark:bg-gray-800">{{ $post->category->name }}</span>
                        @endif
                        <time datetime="{{ $post->published_at->toDateString() }}">
                            {{ $post->published_at->format('M j, Y') }}
                        </time>
                        <span>·</span>
                        <span>{{ $post->reading_time }} min read</span>
                    </div>
                    <h2 class="mt-2 text-xl font-semibold group-hover:underline">{{ $post->title }}</h2>
                    <p class="mt-1 text-gray-600 dark:text-gray-400">{{ $post->excerpt }}</p>
                </a>
            </article>
        @empty
            <p class="py-12 text-center text-gray-500">No articles found. Try a different search.</p>
        @endforelse
    </div>

    <div class="mt-10">
        {{ $posts->links() }}
    </div>
</div>
