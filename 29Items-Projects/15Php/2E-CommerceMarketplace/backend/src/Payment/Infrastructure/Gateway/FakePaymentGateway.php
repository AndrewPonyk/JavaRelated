<?php

declare(strict_types=1);

namespace App\Payment\Infrastructure\Gateway;

use App\Payment\Domain\Gateway\PaymentDeclinedException;
use App\Payment\Domain\Gateway\PaymentGateway;
use App\Shared\Domain\ValueObject\Money;
use Psr\Log\LoggerInterface;

/**
 * Stand-in PSP adapter for local/dev/test. A production deployment swaps this for
 * a real Stripe/Adyen adapter (bound in services.yaml) without touching the
 * application layer. Deterministic, testable behaviour:
 *  - an empty token or a token containing "decline" is declined;
 *  - a non-positive amount is declined;
 *  - otherwise a pseudo PSP reference is returned.
 */
final readonly class FakePaymentGateway implements PaymentGateway
{
    public function __construct(private LoggerInterface $logger)
    {
    }

    public function capture(string $orderId, Money $amount, string $paymentMethodToken): string
    {
        if ('' === trim($paymentMethodToken)) {
            throw PaymentDeclinedException::withReason('missing payment method');
        }
        if (str_contains(strtolower($paymentMethodToken), 'decline')) {
            throw PaymentDeclinedException::withReason('card declined');
        }
        if ($amount->amountMinor <= 0) {
            throw PaymentDeclinedException::withReason('invalid amount');
        }

        $reference = 'psp_'.bin2hex(random_bytes(10));

        $this->logger->info('Captured payment via fake PSP', [
            'orderId' => $orderId,
            'amountMinor' => $amount->amountMinor,
            'currency' => $amount->currency,
            'pspReference' => $reference,
        ]);

        return $reference;
    }
}
