<?php

namespace App\Http\Requests\V1;

use App\Models\Product;
use Illuminate\Foundation\Http\FormRequest;

/**
 * Input + authorization boundary for product updates.
 * Ownership is checked here so the controller can stay assumption-free.
 */
class UpdateProductRequest extends FormRequest
{
    public function authorize(): bool
    {
        /** @var Product|null $product */
        $product = $this->route('product');

        if ($product === null || $this->user() === null) {
            return false;
        }

        // Owner or admin only. (Could delegate to a ProductPolicy.)
        return $this->user()->id === $product->user_id || $this->user()->isAdmin();
    }

    /**
     * Partial update: every field is optional but validated when present.
     *
     * @return array<string, array<int, string>>
     */
    public function rules(): array
    {
        return [
            'name' => ['sometimes', 'string', 'max:255'],
            'description' => ['sometimes', 'nullable', 'string', 'max:5000'],
            'price_cents' => ['sometimes', 'integer', 'min:0'],
            'currency' => ['sometimes', 'string', 'size:3'],
            'category' => ['sometimes', 'string', 'max:100'],
            'stock' => ['sometimes', 'integer', 'min:0'],
            'is_active' => ['sometimes', 'boolean'],
        ];
    }
}
