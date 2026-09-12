<div>
    @push('head')
        <x-seo :seo="$seo" />
    @endpush

    <article>
        <header class="mb-8">
            <div class="flex items-center gap-2 text-sm text-gray-500">
                @if ($post->category)
                    <a href="{{ route('home', ['category' => $post->category->slug]) }}"
                       class="rounded bg-gray-100 px-2 py-0.5 hover:underline dark:bg-gray-800">
                        {{ $post->category->name }}
                    </a>
                @endif
                @if ($post->published_at)
                    <time datetime="{{ $post->published_at->toDateString() }}">
                        {{ $post->published_at->format('F j, Y') }}
                    </time>
                @else
                    <span class="rounded bg-amber-100 px-2 py-0.5 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300">Draft preview</span>
                @endif
                <span>·</span>
                <span>{{ $post->reading_time }} min read</span>
            </div>

            <h1 class="mt-3 text-4xl font-bold tracking-tight">{{ $post->title }}</h1>

            <p class="mt-3 text-sm text-gray-500">By {{ $post->author?->name }}</p>
        </header>

        {{-- Body HTML is sanitised on save (MarkdownService → HTML Purifier),
             so unescaped output here is safe. `prose` styles it via Tailwind Typography. --}}
        <div class="prose prose-lg max-w-none dark:prose-invert">
            {!! $post->body_html !!}
        </div>

        @if ($post->tags->isNotEmpty())
            <div class="mt-8 flex flex-wrap gap-2">
                @foreach ($post->tags as $tag)
                    <span wire:key="tag-{{ $tag->id }}"
                          class="rounded-full bg-gray-100 px-3 py-1 text-xs text-gray-600 dark:bg-gray-800 dark:text-gray-300">
                        #{{ $tag->name }}
                    </span>
                @endforeach
            </div>
        @endif
    </article>

    {{-- Related posts (TF-IDF). Hidden entirely if the recommender returns nothing. --}}
    @if ($related->isNotEmpty())
        <aside class="mt-16 border-t border-gray-100 pt-8 dark:border-gray-800">
            <h2 class="mb-4 text-lg font-semibold">Related articles</h2>
            <ul class="space-y-4">
                @foreach ($related as $item)
                    <li wire:key="related-{{ $item->id }}">
                        <a href="{{ route('posts.show', $item) }}" wire:navigate
                           class="group block">
                            <span class="font-medium group-hover:underline">{{ $item->title }}</span>
                            <span class="block text-sm text-gray-500">{{ $item->reading_time }} min read</span>
                        </a>
                    </li>
                @endforeach
            </ul>
        </aside>
    @endif
</div>
