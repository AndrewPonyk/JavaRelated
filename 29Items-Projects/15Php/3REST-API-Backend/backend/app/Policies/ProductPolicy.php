<?php

namespace App\Policies;

use App\Models\Product;
use App\Models\User;

/**
 * Authorization rules for products. Laravel 11 auto-discovers this policy
 * by convention (Product → ProductPolicy), so no manual registration is
 * needed. Backs the $this->authorize('delete', $product) call in
 * ProductController and the ARCHITECTURE.md §2.5 RBAC story.
 */
class ProductPolicy
{
    /** Owner or admin may update. */
    public function update(User $user, Product $product): bool
    {
        return $user->id === $product->user_id || $user->isAdmin();
    }

    /** Owner or admin may delete. */
    public function delete(User $user, Product $product): bool
    {
        return $user->id === $product->user_id || $user->isAdmin();
    }
}
