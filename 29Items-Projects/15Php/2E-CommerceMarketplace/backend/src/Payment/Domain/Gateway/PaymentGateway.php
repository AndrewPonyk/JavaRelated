<?php

declare(strict_types=1);

namespace App\Payment\Domain\Gateway;

use App\Shared\Domain\ValueObject\Money;

/**
 * Port to a Payment Service Provider (Stripe/Adyen/…). The application depends on
 * this abstraction; concrete PSP integrations live behind adapters in
 * Infrastructure. Card data never crosses this boundary — only a tokenised
 * payment method reference does.
 */
interface PaymentGateway
{
    /**
     * Capture funds for an order. Returns the PSP transaction reference to store.
     *
     * @throws PaymentDeclinedException if the PSP declines the charge
     */
    public function capture(string $orderId, Money $amount, string $paymentMethodToken): string;
}
