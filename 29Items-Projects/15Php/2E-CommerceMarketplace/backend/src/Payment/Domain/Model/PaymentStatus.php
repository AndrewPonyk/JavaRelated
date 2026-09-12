<?php

declare(strict_types=1);

namespace App\Payment\Domain\Model;

enum PaymentStatus: string
{
    case Pending = 'PENDING';
    case Authorized = 'AUTHORIZED';
    case Captured = 'CAPTURED';
    case Refunded = 'REFUNDED';
    case Failed = 'FAILED';
}
