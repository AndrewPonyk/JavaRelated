<?php

declare(strict_types=1);

namespace App\Exceptions;

use RuntimeException;

/**
 * Thrown when a draft/unpublished post is requested through a public surface.
 * The exception handler (bootstrap/app.php) maps this to a 404 so we never
 * reveal the existence of unpublished content.
 */
class PostNotPublishedException extends RuntimeException
{
    public static function forSlug(string $slug): self
    {
        return new self("Post [{$slug}] is not published.");
    }
}
