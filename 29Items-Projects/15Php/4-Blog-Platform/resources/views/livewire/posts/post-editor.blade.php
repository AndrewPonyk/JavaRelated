<div x-data="{ showPreview: true }">
    <div class="mb-6 flex items-center justify-between">
        <h1 class="text-2xl font-bold">{{ $post ? 'Edit' : 'New' }} post</h1>
        {{-- Alpine toggles the preview pane locally — no server round-trip. --}}
        <label class="flex items-center gap-2 text-sm text-gray-600 dark:text-gray-400">
            <input type="checkbox" x-model="showPreview" class="rounded">
            Live preview
        </label>
    </div>

    <form wire:submit="save" class="space-y-5">
        <div>
            <label for="title" class="block text-sm font-medium">Title</label>
            <input id="title" type="text" wire:model.blur="title"
                   class="mt-1 w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800">
            @error('title') <p class="mt-1 text-sm text-red-600">{{ $message }}</p> @enderror
        </div>

        <div class="grid gap-5 sm:grid-cols-3">
            <div>
                <label for="slug" class="block text-sm font-medium">Slug <span class="text-gray-400">(optional)</span></label>
                <input id="slug" type="text" wire:model.blur="slug" placeholder="auto-generated from title"
                       class="mt-1 w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800">
                @error('slug') <p class="mt-1 text-sm text-red-600">{{ $message }}</p> @enderror
            </div>
            <div>
                <label for="status" class="block text-sm font-medium">Status</label>
                <select id="status" wire:model.live="status"
                        class="mt-1 w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800">
                    <option value="draft">Draft</option>
                    <option value="published">Published</option>
                </select>
            </div>
            <div>
                <label for="publishedAt" class="block text-sm font-medium">
                    Publish date <span class="text-gray-400">(optional)</span>
                </label>
                <input id="publishedAt" type="datetime-local" wire:model="publishedAt"
                       @disabled($status !== 'published')
                       class="mt-1 w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800 disabled:opacity-50">
                <p class="mt-1 text-xs text-gray-400">A future date schedules the post.</p>
                @error('publishedAt') <p class="mt-1 text-sm text-red-600">{{ $message }}</p> @enderror
            </div>
        </div>

        {{-- Editor + live preview, side by side. --}}
        <div class="grid gap-4" :class="showPreview ? 'lg:grid-cols-2' : 'grid-cols-1'">
            <div>
                <label for="body" class="block text-sm font-medium">Body <span class="text-gray-400">(Markdown)</span></label>
                <textarea id="body" rows="20"
                          wire:model.live.debounce.500ms="body"
                          class="mt-1 w-full rounded-md border-gray-300 font-mono text-sm dark:border-gray-700 dark:bg-gray-800"
                          placeholder="# Write your post in Markdown…"></textarea>
                @error('body') <p class="mt-1 text-sm text-red-600">{{ $message }}</p> @enderror
            </div>

            <div x-show="showPreview" x-cloak>
                <span class="block text-sm font-medium">Preview</span>
                <div class="prose mt-1 max-w-none rounded-md border border-gray-200 p-4 dark:prose-invert dark:border-gray-700"
                     wire:loading.class="opacity-50">
                    {{-- previewHtml is sanitised server-side (same pipeline as save). --}}
                    {!! $this->previewHtml !!}
                </div>
            </div>
        </div>

        <div class="flex items-center gap-3">
            <button type="submit"
                    class="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700 dark:bg-white dark:text-gray-900">
                <span wire:loading.remove wire:target="save">Save post</span>
                <span wire:loading wire:target="save">Saving…</span>
            </button>
            <a href="{{ route('admin.posts') }}" wire:navigate class="text-sm text-gray-500 hover:underline">Cancel</a>
        </div>
    </form>
</div>
