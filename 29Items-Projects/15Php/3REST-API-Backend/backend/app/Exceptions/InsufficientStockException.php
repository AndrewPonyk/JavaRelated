<?php

namespace App\Exceptions;

use RuntimeException;

/**
 * Domain exception thrown when a purchase exceeds available stock.
 * The central handler in bootstrap/app.php (Laravel 11 has no Handler.php)
 * can map domain exceptions like this to an appropriate HTTP status.
 *
 * Throwing typed exceptions keeps controllers free of defensive try/catch
 * (see docs/ARCHITECTURE.md §2.6, principle 3).
 */
class InsufficientStockException extends RuntimeException
{
    public static function for(int $productId, int $requested, int $available): self
    {
        return new self(
            "Insufficient stock for product {$productId}: requested {$requested}, available {$available}.",
        );
    }
}
