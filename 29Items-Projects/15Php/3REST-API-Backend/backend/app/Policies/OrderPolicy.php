<?php

namespace App\Policies;

use App\Models\Order;
use App\Models\User;

/**
 * Authorization for orders. Auto-discovered by Laravel (Order → OrderPolicy).
 */
class OrderPolicy
{
    /** A user may view only their own orders; admins may view any. */
    public function view(User $user, Order $order): bool
    {
        return $user->id === $order->user_id || $user->isAdmin();
    }
}
