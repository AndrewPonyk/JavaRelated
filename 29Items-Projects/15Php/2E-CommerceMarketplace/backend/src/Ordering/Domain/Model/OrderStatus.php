<?php

declare(strict_types=1);

namespace App\Ordering\Domain\Model;

enum OrderStatus: string
{
    case Pending = 'PENDING';
    case Paid = 'PAID';
    case Shipped = 'SHIPPED';
    case Cancelled = 'CANCELLED';
}
