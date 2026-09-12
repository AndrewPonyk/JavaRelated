<div>
    <h1 class="mb-6 text-2xl font-bold">Categories</h1>

    <div class="grid gap-8 lg:grid-cols-3">
        {{-- Create / edit form --}}
        <form wire:submit="save" class="space-y-4 lg:col-span-1">
            <h2 class="font-semibold">{{ $editingId ? 'Edit category' : 'New category' }}</h2>
            <div>
                <label for="name" class="block text-sm font-medium">Name</label>
                <input id="name" type="text" wire:model="name"
                       class="mt-1 w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800">
                @error('name') <p class="mt-1 text-sm text-red-600">{{ $message }}</p> @enderror
            </div>
            <div>
                <label for="description" class="block text-sm font-medium">Description</label>
                <textarea id="description" rows="3" wire:model="description"
                          class="mt-1 w-full rounded-md border-gray-300 dark:border-gray-700 dark:bg-gray-800"></textarea>
                @error('description') <p class="mt-1 text-sm text-red-600">{{ $message }}</p> @enderror
            </div>
            <div class="flex gap-2">
                <button type="submit"
                        class="rounded-md bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700 dark:bg-white dark:text-gray-900">
                    {{ $editingId ? 'Update' : 'Create' }}
                </button>
                @if ($editingId)
                    <button type="button" wire:click="resetForm" class="text-sm text-gray-500 hover:underline">Cancel</button>
                @endif
            </div>
        </form>

        {{-- List --}}
        <div class="lg:col-span-2">
            <ul class="divide-y divide-gray-100 rounded-lg border border-gray-100 dark:divide-gray-800 dark:border-gray-800">
                @forelse ($categories as $category)
                    <li wire:key="cat-{{ $category->id }}" class="flex items-center justify-between px-4 py-3">
                        <div>
                            <span class="font-medium">{{ $category->name }}</span>
                            <span class="ml-2 text-xs text-gray-500">{{ $category->posts_count }} posts</span>
                            @if ($category->description)
                                <p class="text-sm text-gray-500">{{ $category->description }}</p>
                            @endif
                        </div>
                        <div class="text-sm">
                            <button type="button" wire:click="edit({{ $category->id }})" class="text-blue-600 hover:underline">Edit</button>
                            <button type="button" wire:click="delete({{ $category->id }})"
                                    wire:confirm="Delete “{{ $category->name }}”?"
                                    class="ml-3 text-red-600 hover:underline">Delete</button>
                        </div>
                    </li>
                @empty
                    <li class="px-4 py-8 text-center text-gray-500">No categories yet.</li>
                @endforelse
            </ul>
        </div>
    </div>
</div>
