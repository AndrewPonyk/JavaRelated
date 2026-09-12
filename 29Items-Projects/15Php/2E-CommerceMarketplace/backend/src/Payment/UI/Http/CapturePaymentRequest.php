<?php

declare(strict_types=1);

namespace App\Payment\UI\Http;

use Symfony\Component\Validator\Constraints as Assert;

final class CapturePaymentRequest
{
    #[Assert\NotBlank]
    #[Assert\Uuid]
    public string $orderId = '';

    #[Assert\Positive]
    public int $amountMinor = 0;

    #[Assert\NotBlank]
    #[Assert\Currency]
    public string $currency = 'USD';

    /** Tokenised payment method from the PSP — never a raw card number. */
    #[Assert\NotBlank]
    public string $paymentMethodToken = '';

    /** Optional client-supplied idempotency key; generated server-side if absent. */
    #[Assert\Length(max: 64)]
    public ?string $idempotencyKey = null;
}
