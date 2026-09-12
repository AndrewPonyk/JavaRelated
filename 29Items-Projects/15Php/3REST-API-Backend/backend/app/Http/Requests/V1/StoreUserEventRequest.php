<?php

namespace App\Http\Requests\V1;

use App\Models\UserEvent;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;

/**
 * Records a behavioral signal (view / add_to_cart / wishlist). Purchase
 * signals are written by the order flow, not this endpoint, so they can't
 * be faked by a client.
 */
class StoreUserEventRequest extends FormRequest
{
    public function authorize(): bool
    {
        return $this->user() !== null;
    }

    /**
     * @return array<string, mixed>
     */
    public function rules(): array
    {
        return [
            'product_id' => ['required', 'integer', 'exists:products,id'],
            'type' => ['required', 'string', Rule::in([
                UserEvent::TYPE_VIEW,
                UserEvent::TYPE_ADD_TO_CART,
                UserEvent::TYPE_WISHLIST,
            ])],
        ];
    }
}
