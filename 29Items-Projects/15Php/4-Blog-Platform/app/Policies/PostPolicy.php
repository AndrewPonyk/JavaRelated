<?php

declare(strict_types=1);

namespace App\Policies;

use App\Models\Post;
use App\Models\User;

/**
 * Authorisation for managing posts.
 *
 * This is a single-author blog: any authenticated user is the admin and may
 * manage all posts. The policy is auto-discovered by Laravel (App\Policies\
 * PostPolicy ↔ App\Models\Post), so no manual registration is needed.
 *
 * If the platform ever grows to multiple authors, tighten update/delete to
 * `$post->user_id === $user->id`.
 */
class PostPolicy
{
    public function viewAny(User $user): bool
    {
        return true;
    }

    public function view(User $user, Post $post): bool
    {
        return true;
    }

    public function create(User $user): bool
    {
        return true;
    }

    public function update(User $user, Post $post): bool
    {
        return true;
    }

    public function delete(User $user, Post $post): bool
    {
        return true;
    }
}
