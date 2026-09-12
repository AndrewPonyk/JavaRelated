<?php

declare(strict_types=1);

namespace App\Tests\Support;

use App\Payment\Domain\Model\PaymentHold;
use App\Payment\Domain\Repository\PaymentHoldRepository;

final class InMemoryPaymentHoldRepository implements PaymentHoldRepository
{
    /** @var array<string, PaymentHold> */
    public array $byOrder = [];

    public function add(PaymentHold $hold): void
    {
        $this->byOrder[$hold->orderId()] = $hold;
    }

    public function existsForOrder(string $orderId): bool
    {
        return isset($this->byOrder[$orderId]);
    }

    public function removeForOrder(string $orderId): void
    {
        unset($this->byOrder[$orderId]);
    }
}
