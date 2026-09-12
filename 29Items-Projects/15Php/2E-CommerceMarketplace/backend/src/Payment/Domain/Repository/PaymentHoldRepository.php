<?php

declare(strict_types=1);

namespace App\Payment\Domain\Repository;

use App\Payment\Domain\Model\PaymentHold;

interface PaymentHoldRepository
{
    public function add(PaymentHold $hold): void;

    public function existsForOrder(string $orderId): bool;

    public function removeForOrder(string $orderId): void;
}
