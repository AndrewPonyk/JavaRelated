<?php

declare(strict_types=1);

namespace App\Livewire\Admin;

use App\Models\Category;
use Illuminate\Contracts\View\View;
use Illuminate\Foundation\Auth\Access\AuthorizesRequests;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
use Livewire\Component;

/**
 * Full CRUD for categories. Inline create/edit form + list with delete.
 */
class CategoryManager extends Component
{
    use AuthorizesRequests;

    public ?int $editingId = null;

    public string $name = '';

    public string $description = '';

    /**
     * @return array<string, mixed>
     */
    protected function rules(): array
    {
        return [
            'name' => [
                'required', 'string', 'max:255',
                Rule::unique('categories', 'name')->ignore($this->editingId),
            ],
            'description' => ['nullable', 'string', 'max:500'],
        ];
    }

    public function edit(int $id): void
    {
        $category = Category::findOrFail($id);
        $this->authorize('update', $category);

        $this->editingId = $category->id;
        $this->name = $category->name;
        $this->description = (string) $category->description;
    }

    public function save(): void
    {
        $validated = $this->validate();

        if ($this->editingId) {
            $category = Category::findOrFail($this->editingId);
            $this->authorize('update', $category);
            $category->update([
                'name' => $validated['name'],
                'slug' => Str::slug($validated['name']),
                'description' => $validated['description'] ?: null,
            ]);
            session()->flash('status', 'Category updated.');
        } else {
            $this->authorize('create', Category::class);
            Category::create([
                'name' => $validated['name'],
                'slug' => Str::slug($validated['name']),
                'description' => $validated['description'] ?: null,
            ]);
            session()->flash('status', 'Category created.');
        }

        $this->resetForm();
    }

    public function delete(int $id): void
    {
        $category = Category::findOrFail($id);
        $this->authorize('delete', $category);

        // Posts keep existing; their category_id is nulled by the FK constraint.
        $category->delete();
        session()->flash('status', 'Category deleted.');
    }

    public function resetForm(): void
    {
        $this->reset(['editingId', 'name', 'description']);
        $this->resetValidation();
    }

    public function render(): View
    {
        return view('livewire.admin.category-manager', [
            'categories' => Category::query()->withCount('posts')->orderBy('name')->get(),
        ]);
    }
}
